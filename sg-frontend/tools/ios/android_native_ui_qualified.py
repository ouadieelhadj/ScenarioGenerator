"""Installed-APK test using Android's built-in accessibility dump, not legacy instrumentation.

Adapted from the protected dump/focus/stdin primitives qualified by the DAMANCASH
session. This module never imports or executes that session's business scenarios.
No WebView debugging, permission grant, FLAG_SECURE override or cleartext fallback.
"""
import argparse
import copy
import json
from pathlib import Path
import re
import shlex
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET

PACKAGE = 'com.moneycore.merchantportal.test'
DOCUMENT_PACKAGES = ('com.google.android.documentsui', 'com.android.documentsui')
DOCUMENT_COMPONENTS = tuple(package + '/' + activity for package in DOCUMENT_PACKAGES
                            for activity in ('com.android.documentsui.picker.PickActivity', '.picker.PickActivity'))
PERMISSION_PACKAGES = ('com.google.android.permissioncontroller', 'com.android.permissioncontroller')
PERMISSION_COMPONENTS = tuple(package + '/com.android.permissioncontroller.permission.ui.GrantPermissionsActivity'
                              for package in PERMISSION_PACKAGES)
FILE_EXTENSIONS = {'application/pdf': 'pdf', 'image/png': 'png', 'image/jpeg': 'jpg'}


class NativeFailure(RuntimeError):
    """Only fixed diagnostic codes may cross the process boundary."""


def parse_hierarchy(raw):
    start, end = raw.find('<?xml'), raw.find('</hierarchy>')
    if len(raw) > 2_000_000 or start < 0 or end < 0 or '<!DOCTYPE' in raw:
        raise NativeFailure('HIERARCHY_INCOMPLETE')
    try:
        return ET.fromstring(raw[start:end + len('</hierarchy>')])
    except ET.ParseError:
        raise NativeFailure('HIERARCHY_INVALID') from None


def redacted_hierarchy(tree, private_values=()):
    safe = copy.deepcopy(tree)
    for node in safe.iter():
        editable = node.get('password') == 'true' or node.get('class') == 'android.widget.EditText'
        if editable:
            node.set('input-present', str(bool(node.get('text'))).lower())
        for key, value in list(node.attrib.items()):
            if editable and key in ('text', 'content-desc', 'hint'):
                node.set(key, '[INPUT_REDACTED]')
            else:
                for private in private_values:
                    if private:
                        value = value.replace(private, '[PRIVATE_REDACTED]')
                node.set(key, value)
    return safe


def bounds(node):
    match = re.fullmatch(r'\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]', node.get('bounds', ''))
    return tuple(map(int, match.groups())) if match else (0, 0, 0, 0)


def exposed_point(node, width, height):
    left, top, right, bottom = bounds(node)
    label = node.get('text', '') + ' ' + node.get('content-desc', '')
    toolbar = 'Ouvrir le menu' in label or 'Fermer le menu' in label
    top = max(top, round((128 if toolbar else 240) * width / 720))
    bottom = min(bottom, height - round(48 * width / 720))
    left, right = max(0, left), min(width, right)
    if node.get('enabled') != 'true' or right <= left or bottom <= top:
        return None
    return (left + right) // 2, (top + bottom) // 2


def require_foreground(raw, allow_system_dialog=False):
    focused = [line for line in raw.splitlines() if 'mFocusedApp=' in line]
    current = [line for line in raw.splitlines() if 'mCurrentFocus=' in line]
    if not any(PACKAGE + '/' in line for line in focused):
        raise NativeFailure('APP_NOT_FOREGROUND_NO_INPUT')
    if not allow_system_dialog and not any(PACKAGE + '/' in line for line in current):
        raise NativeFailure('SYSTEM_DIALOG_NO_INPUT')


def require_documents_foreground(raw):
    # Only the exact delegated system document activity, never an arbitrary permission/dialog/app.
    for field in ('mFocusedApp=', 'mCurrentFocus='):
        lines = [line for line in raw.splitlines() if field in line]
        if len(lines) != 1 or not any(component in lines[0] and
                re.search(re.escape(component) + r'(?:\s|\})', lines[0]) for component in DOCUMENT_COMPONENTS):
            raise NativeFailure('DOCUMENT_PICKER_NOT_FOREGROUND_NO_INPUT')


def require_camera_permission_foreground(raw):
    for field in ('mFocusedApp=', 'mCurrentFocus='):
        lines = [line for line in raw.splitlines() if field in line]
        if len(lines) != 1 or not any(re.search(re.escape(component)+r'(?:\s|\})',lines[0]) for component in PERMISSION_COMPONENTS):
            raise NativeFailure('CAMERA_PERMISSION_NOT_FOREGROUND_NO_INPUT')


def camera_deny_control(tree):
    labels = ' '.join(node.get('text','') for node in tree.iter('node'))
    if ('Incidents FuturPayment RECETTE' not in labels or not any(text in labels.lower() for text in
            ('take pictures and record video', 'prendre des photos et enregistrer des vidéos'))):
        raise NativeFailure('OWNED_CAMERA_PERMISSION_NOT_CONFIRMED_NO_ACTION')
    # Android uses a second exact deny id after a previous refusal. Neither
    # branch grants access; more than one matching button is still refused.
    deny_ids=tuple(package+':id/'+name for package in PERMISSION_PACKAGES for name in
                   ('permission_deny_button','permission_deny_and_dont_ask_again_button'))
    buttons = [node for node in tree.iter('node') if node.get('package') in PERMISSION_PACKAGES and
               node.get('resource-id') in deny_ids and
               node.get('class')=='android.widget.Button' and node.get('clickable')=='true' and node.get('enabled')=='true']
    if len(buttons)!=1: raise NativeFailure('CAMERA_DENIAL_AMBIGUOUS_NO_ACTION')
    return buttons[0]


def photo_capture_control(tree, reference, subject):
    require_ticket(tree, reference, subject)
    label = 'Prendre une photo (facultatif)'
    matches = {}
    for row in tree.iter('node'):
        if label not in ' '.join(node.get('text','')+' '+node.get('content-desc','') for node in row.iter('node')):continue
        buttons = [node for node in row.iter('node') if node.get('class')=='android.widget.Button']
        if len(buttons)==1 and buttons[0].get('package')==PACKAGE and buttons[0].get('enabled')=='true' and buttons[0].get('clickable')=='true':
            matches[id(buttons[0])]=buttons[0]
    if len(matches)!=1: raise NativeFailure('PHOTO_CAPTURE_AMBIGUOUS_NO_TAP')
    return next(iter(matches.values()))


def creation_category_control(tree):
    nodes=list(tree.iter('node'))
    if 'Nouvelle déclaration' not in {node.get('text') for node in nodes}:raise NativeFailure('CREATION_FORM_NOT_CONFIRMED')
    labels={text:[i for i,node in enumerate(nodes) if node.get('class')=='android.widget.TextView' and node.get('text')==text] for text in ('Catégorie','Objet')}
    if any(len(indices)!=1 for indices in labels.values()) or labels['Catégorie'][0]>=labels['Objet'][0]:
        raise NativeFailure('CATEGORY_FORM_NOT_CONFIRMED_NO_TAP')
    controls=[node for node in nodes[labels['Catégorie'][0]+1:labels['Objet'][0]] if node.get('class') in ('android.view.View','android.widget.Spinner') and
              node.get('package')==PACKAGE and node.get('clickable')=='true' and node.get('enabled')=='true']
    if len(controls)!=1:raise NativeFailure('CATEGORY_CONTROL_AMBIGUOUS_NO_TAP')
    return controls[0]


def tpe_category_option(tree):
    choices=[node for node in tree.iter('node') if node.get('class')=='android.widget.CheckedTextView' and
             node.get('resource-id')=='android:id/text1' and node.get('package')==PACKAGE and node.get('text')=='TPE et connexion' and node.get('enabled')=='true']
    if len(choices)!=1:raise NativeFailure('TPE_CATEGORY_NOT_CONFIRMED_NO_TAP')
    return choices[0]


def other_category_option(tree):
    choices=[node for node in tree.iter('node') if node.get('class')=='android.widget.CheckedTextView' and
             node.get('resource-id')=='android:id/text1' and node.get('package')==PACKAGE and
             node.get('text')=='Autre' and node.get('enabled')=='true']
    if len(choices)!=1:raise NativeFailure('OTHER_CATEGORY_NOT_CONFIRMED_NO_TAP')
    return choices[0]


def invalid_incident_fields(tree):
    labels={node.get('text') for node in tree.iter('node')}
    fields=[node for node in tree.iter('node') if node.get('class')=='android.widget.EditText']
    if (not {'Nouvelle déclaration','Objet','Description'}.issubset(labels) or len(fields)!=2 or
            any(node.get('package')!=PACKAGE or node.get('password')!='false' or node.get('enabled')!='true' for node in fields)):
        raise NativeFailure('INVALID_FORM_IDENTITY_NOT_CONFIRMED_NO_SEND')
    if fields[1].get('text','') or any((node.get('text','')+' '+node.get('content-desc','')).startswith('Dépôt confirmé :') for node in tree.iter('node')):
        raise NativeFailure('FORM_NOT_INCOMPLETE_OR_ALREADY_CONFIRMED_NO_SEND')
    return fields


def attachment_export_name(item):
    if (not isinstance(item, dict) or item.get('contentType') not in FILE_EXTENSIONS or
            not re.fullmatch(r'[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}', item.get('id', '')) or
            not re.fullmatch(r'[a-f0-9]{64}', item.get('sha256', ''))):
        raise NativeFailure('ATTACHMENT_FIXTURE_INVALID')
    return 'incident-' + item['id'] + '.' + FILE_EXTENSIONS[item['contentType']]


def document_save_controls(tree, filename):
    fields = [node for node in tree.iter('node') if node.get('class') == 'android.widget.EditText']
    if (len(fields) != 1 or fields[0].get('text') != filename or fields[0].get('enabled') != 'true' or
            fields[0].get('package') not in DOCUMENT_PACKAGES):
        raise NativeFailure('DOCUMENT_FILENAME_NOT_CONFIRMED_NO_ACTION')
    save = [node for node in tree.iter('node') if node.get('package') in DOCUMENT_PACKAGES and
            node.get('class') == 'android.widget.Button' and node.get('enabled') == 'true' and
            node.get('clickable') == 'true' and node.get('text', '').upper() in ('SAVE', 'ENREGISTRER')]
    if len(save) != 1:
        raise NativeFailure('DOCUMENT_SAVE_AMBIGUOUS_NO_ACTION')
    return save[0]


def document_ime_visible(raw):
    # mIsInputViewShown can describe a stale view after Back; use the service's
    # authoritative shown flag, and refuse missing/ambiguous observations.
    states=re.findall(r'\bmInputShown=(true|false)\b',raw)
    if len(states)!=1:raise NativeFailure('DOCUMENT_IME_STATE_NOT_CONFIRMED_NO_INPUT')
    return states[0]=='true'


def attachment_download_control(tree, mime, reference, subject):
    require_ticket(tree, reference, subject)
    if mime not in FILE_EXTENSIONS:
        raise NativeFailure('ATTACHMENT_MIME_INVALID')
    matches = {}
    for row in tree.iter('node'):
        text = ' '.join(child.get('text', '') for child in row.iter('node'))
        if mime not in text or 'CLEAN' not in text or any(other in text for other in FILE_EXTENSIONS if other != mime):
            continue
        for button in row.iter('node'):
            if (button.get('package') == PACKAGE and button.get('class') == 'android.widget.Button' and
                    button.get('text') == 'Télécharger' and button.get('enabled') == 'true' and button.get('clickable') == 'true'):
                matches[id(button)] = button
    if len(matches) != 1:
        raise NativeFailure('ATTACHMENT_DOWNLOAD_AMBIGUOUS_NO_TAP')
    return next(iter(matches.values()))


def require_system_recovery(raw):
    # Recovery on the verified emulator before the app opens, not a generic dialog allowance.
    current=[line for line in raw.splitlines() if 'mCurrentFocus=' in line]
    focused=[line for line in raw.splitlines() if 'mFocusedApp=' in line]
    if not any('Application Not Responding: com.android.systemui' in line for line in current):
        raise NativeFailure('SYSTEM_ALERT_CHANGED_NO_TAP')
    if not any(PACKAGE+'/' in line or 'com.google.android.apps.nexuslauncher/.NexusLauncherActivity' in line for line in focused):
        raise NativeFailure('OTHER_APP_NO_TAP')


def private_input_command(value, clear_existing=True):
    if not isinstance(value, str) or not 1 <= len(value) <= 1000 or '%s' in value or any(ord(c) < 32 or ord(c) > 126 for c in value):
        raise NativeFailure('INPUT_NOT_PRINTABLE_ASCII')
    # The complete command enters adb's stdin, never argv or a diagnostic file.
    if not isinstance(clear_existing, bool):
        raise NativeFailure('INPUT_CLEAR_MODE_INVALID')
    return (('input keycombination 113 29 && input keyevent 67 && ' if clear_existing else '') + 'input text ' +
            shlex.quote(value.replace(' ', '%s')) + '\n').encode('ascii')


def paced_private_input_command(value, clear_existing=True):
    private_input_command(value,clear_existing)  # Same validation and shell quoting contract.
    chunks=['input text '+shlex.quote(value[index:index+4].replace(' ','%s')) for index in range(0,len(value),4)]
    prefix='input keycombination 113 29 && input keyevent 67 && sleep 0.05 && ' if clear_existing else ''
    return (prefix+' && sleep 0.05 && '.join(chunks)+'\n').encode('ascii')


def login_input_summary(tree, fixture):
    fields = [node for node in tree.iter('node') if node.get('class') == 'android.widget.EditText']
    users = [node for node in fields if node.get('password') == 'false']
    passwords = [node for node in fields if node.get('password') == 'true']
    if len(fields) != 2 or len(users) != 1 or len(passwords) != 1:
        raise NativeFailure('LOGIN_FIELDS_AMBIGUOUS_NO_INPUT')
    return {'loginMatches': users[0].get('text') == fixture['login'],
            'passwordLengthMatches': len(passwords[0].get('text', '')) == len(fixture['password'])}


def login_field_nodes(tree):
    fields=[node for node in tree.iter('node') if node.get('class')=='android.widget.EditText']
    users=[node for node in fields if node.get('password')=='false']
    passwords=[node for node in fields if node.get('password')=='true']
    if len(fields)!=2 or len(users)!=1 or len(passwords)!=1 or any(node.get('package')!=PACKAGE for node in fields):
        raise NativeFailure('LOGIN_FIELDS_AMBIGUOUS_NO_INPUT')
    return users[0],passwords[0]


def context_option(tree, expected_label='Synthetic commerce'):
    # Native HTML-select rows are CheckedTextView with clickable=false. They
    # are not headings: accept only this exact owned context picker/list.
    lists = [node for node in tree.iter('node') if node.get('class') == 'android.widget.ListView'
             and any(child.get('text') == 'Choisir un dossier autorisé' for child in node.iter('node'))]
    choices = [node for group in lists for node in group.iter('node')
               if node.get('class') == 'android.widget.CheckedTextView' and node.get('text') == expected_label
               and node.get('resource-id') == 'android:id/text1' and node.get('package') == PACKAGE
               and node.get('enabled') == 'true']
    if len(lists) != 1 or len(choices) != 1:
        raise NativeFailure('CONTEXT_PICKER_AMBIGUOUS_NO_TAP')
    return choices[0]


def creation_context_control(tree):
    labels={node.get('text','') for node in tree.iter('node')}
    if not {'Nouvelle déclaration','Commerce ou dossier bénéficiaire'}.issubset(labels):
        raise NativeFailure('CREATION_FORM_NOT_CONFIRMED')
    choices=[node for node in tree.iter('node') if node.get('text') in ('Choisir un dossier autorisé','Commerce ou dossier bénéficiaire')
             and node.get('clickable')=='true' and node.get('enabled')=='true' and node.get('package')==PACKAGE]
    if len(choices)!=1:raise NativeFailure('CONTEXT_CONTROL_AMBIGUOUS_NO_TAP')
    return choices[0]


def context_scroll_up(tree,control):
    # Ignore a fixed toolbar outside the control's scrolling content. Its
    # nearby DOM position does not tell us where an off-screen field is.
    parents={child:parent for parent in tree.iter() for child in parent}
    container=parents.get(control)
    while container is not None and container.get('scrollable')!='true':
        container=parents.get(container)
    nodes=list((container if container is not None else tree).iter('node'));position=nodes.index(control)
    visible=[index for index,node in enumerate(nodes) if node.get('class')=='android.widget.TextView' and node.get('text')
             and bounds(node)[3]>bounds(node)[1]]
    # DOM order stays usable when WebView reports zero bounds for an off-screen control.
    if not visible:return False
    nearest=min(visible,key=lambda index:abs(index-position))
    return nearest>position


def conversation_input(tree, reference, subject):
    labels=[node.get('text','') for node in tree.iter('node')]
    if (subject not in labels or not any(reference in value for value in labels) or
            not {'Échanges','Action autorisée','Message ou motif'}.issubset(labels)):
        raise NativeFailure('CONVERSATION_IDENTITY_NOT_CONFIRMED_NO_INPUT')
    fields=[node for node in tree.iter('node') if node.get('class')=='android.widget.EditText']
    if (len(fields)!=1 or fields[0].get('package')!=PACKAGE or fields[0].get('password')!='false' or
            fields[0].get('enabled')!='true'):
        raise NativeFailure('CONVERSATION_FIELD_AMBIGUOUS_NO_INPUT')
    return fields[0]


def require_ticket(tree, reference, subject):
    labels=[node.get('text','') for node in tree.iter('node') if node.get('class')=='android.widget.TextView']
    if subject not in labels or not any(value.startswith(reference+' · ') for value in labels):
        raise NativeFailure('TICKET_IDENTITY_NOT_CONFIRMED_NO_ACTION')


def draft_edit_fields(tree, reference, subject):
    require_ticket(tree,reference,subject)
    nodes=list(tree.iter('node'))
    # Android exposes a select twice: its TextView label and clickable View.
    # Only the semantic labels delimit the editor, never the select itself.
    labels={label:[i for i,node in enumerate(nodes) if node.get('text')==label and node.get('class')=='android.widget.TextView'] for label in ('Modifier le brouillon','Objet','Description','Impact')}
    if any(len(indices)!=1 for indices in labels.values()):raise NativeFailure('DRAFT_EDITOR_NOT_CONFIRMED_NO_INPUT')
    heading,subject_label,description_label,impact_label=(labels[label][0] for label in ('Modifier le brouillon','Objet','Description','Impact'))
    if not heading<subject_label<description_label<impact_label:raise NativeFailure('DRAFT_EDITOR_ORDER_INVALID_NO_INPUT')
    fields=[]
    for start,end in ((subject_label,description_label),(description_label,impact_label)):
        matches=[node for node in nodes[start+1:end] if node.get('class')=='android.widget.EditText']
        if len(matches)!=1 or matches[0].get('package')!=PACKAGE or matches[0].get('password')!='false' or matches[0].get('enabled')!='true':
            raise NativeFailure('DRAFT_FIELDS_AMBIGUOUS_NO_INPUT')
        fields.append(matches[0])
    return fields


def ticket_action_control(tree, reference, subject, options=None):
    require_ticket(tree,reference,subject)
    labels={node.get('text','') for node in tree.iter('node')}
    if 'Action autorisée' not in labels:raise NativeFailure('TICKET_ACTIONS_NOT_CONFIRMED')
    if options is None:options={'Action','Envoyer le brouillon','Modifier la description du brouillon','Annuler avec motif'}
    choices=[node for node in tree.iter('node') if node.get('text') in options and node.get('package')==PACKAGE and
             node.get('clickable')=='true' and node.get('enabled')=='true' and node.get('class') in ('android.view.View','android.widget.Spinner')]
    if len(choices)!=1:raise NativeFailure('TICKET_ACTION_CONTROL_AMBIGUOUS_NO_TAP')
    return choices[0]


def draft_action_option(tree, label):
    allowed={'Envoyer le brouillon','Modifier la description du brouillon','Annuler avec motif'}
    lists=[node for node in tree.iter('node') if node.get('class')=='android.widget.ListView']
    if len(lists)!=1 or label not in allowed:raise NativeFailure('DRAFT_ACTION_PICKER_INVALID_NO_TAP')
    rows=[node for node in lists[0].iter('node') if node.get('class')=='android.widget.CheckedTextView']
    if not rows or any(node.get('text') not in allowed or node.get('package')!=PACKAGE or node.get('resource-id')!='android:id/text1' for node in rows):
        raise NativeFailure('DRAFT_ACTION_PICKER_INVALID_NO_TAP')
    choices=[node for node in rows if node.get('text')==label and node.get('enabled')=='true']
    if len(choices)!=1:raise NativeFailure('DRAFT_ACTION_AMBIGUOUS_NO_TAP')
    return choices[0]


def conversation_state_labels(fixture):
    result={}
    for key in ('expectedStateLabel','expectedReplyStateLabel'):
        if key in fixture:
            if fixture[key] not in ('En attente du commerçant','En cours'):raise NativeFailure('CONVERSATION_STATE_FIXTURE_INVALID')
            result[key]=fixture[key]
    return result


def conversation_forbidden_texts(fixture):
    values=fixture.get('forbiddenTexts',[])
    if (not isinstance(values,list) or len(values)>8 or
        any(not isinstance(value,str) or not 1<=len(value)<=200 or any(ord(c)<32 for c in value) for value in values) or
        len(set(values))!=len(values)):
        raise NativeFailure('CONVERSATION_PRIVACY_FIXTURE_INVALID')
    return tuple(values)


def conversation_service_texts(fixture):
    values=fixture.get('serviceLevelTexts',[])
    if (not isinstance(values,list) or len(values)>6 or
        any(not isinstance(value,str) or not 1<=len(value)<=200 or any(ord(c)<32 for c in value) for value in values) or
        len(set(values))!=len(values)):
        raise NativeFailure('CONVERSATION_SERVICE_LEVEL_FIXTURE_INVALID')
    return tuple(values)


def require_conversation_privacy(tree, forbidden):
    for node in tree.iter('node'):
        displayed=node.get('text','')+' '+node.get('content-desc','')
        if any(value in displayed for value in forbidden):
            raise NativeFailure('FOREIGN_COLLECTIVE_CONTEXT_EXPOSED_NO_REPLY')


def require_revoked_conversation_absent(tree, fixture):
    labels=[node.get('text','')+' '+node.get('content-desc','') for node in tree.iter('node')]
    if not any('Assistance · Incidents' in text for text in labels) or not any(text.strip()=='0 incident(s) dans votre périmètre.' for text in labels):
        raise NativeFailure('REVOKED_EMPTY_LIST_NOT_CONFIRMED')
    for key in ('reference','subject','supportReply'):
        value=fixture.get(key)
        if not isinstance(value,str) or not value or any(value in text for text in labels):
            raise NativeFailure('REVOKED_CONVERSATION_STILL_VISIBLE')
    if any(node.get('class')=='android.widget.EditText' for node in tree.iter('node')):
        raise NativeFailure('REVOKED_CONVERSATION_INPUT_STILL_VISIBLE')


def commercial_conversation_fixture(fixture):
    for key in ('publicReply','reply','internalNote','reference'):
        private_input_command(fixture.get(key))
    if not re.fullmatch(r'INC-[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}',fixture['reference']):
        raise NativeFailure('COMMERCIAL_CONVERSATION_REFERENCE_INVALID')
    if not isinstance(fixture.get('publicAuthor'),str) or not re.fullmatch(r'[A-Za-z0-9_.-]{1,128}',fixture['publicAuthor']):
        raise NativeFailure('COMMERCIAL_CONVERSATION_AUTHOR_INVALID')
    attachment_export_name(fixture.get('attachment'))
    if fixture['attachment']['contentType']!='image/png':raise NativeFailure('COMMERCIAL_CONVERSATION_IMAGE_REQUIRED')


def context_switch_fixture(fixture):
    for key in ('fromCode','toCode'):
        if not re.fullmatch(r'(?:INC|OTH)_[A-F0-9]{8}',fixture.get(key,'')):raise NativeFailure('PERSONAL_CONTEXT_CODE_INVALID')
    if fixture['fromCode']==fixture['toCode']:raise NativeFailure('PERSONAL_CONTEXTS_NOT_DISTINCT')
    for key in ('association','pdvA','pdvB'):
        if not re.fullmatch(r'[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}',fixture.get(key,'')):raise NativeFailure('PERSONAL_CONTEXT_UUID_INVALID')
    if fixture['pdvA']==fixture['pdvB']:raise NativeFailure('PERSONAL_CONTEXT_PDVS_NOT_DISTINCT')
    references=set()
    for name in ('source','first','second'):
        item=fixture.get(name)
        if not isinstance(item,dict):raise NativeFailure('PERSONAL_CONTEXT_TICKET_REQUIRED')
        for key in ('subject','reference','publicReply'):private_input_command(item.get(key))
        if not re.fullmatch(r'INC-[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}',item['reference']):raise NativeFailure('PERSONAL_CONTEXT_REFERENCE_INVALID')
        references.add(item['reference'])
    if len(references)!=3:raise NativeFailure('PERSONAL_CONTEXT_TICKETS_NOT_DISTINCT')
    for key in ('fromLabel','toLabel','associationLabel'):private_input_command(fixture.get(key))
    if fixture.get('subject')!=fixture['source']['subject']:raise NativeFailure('PERSONAL_CONTEXT_SOURCE_MISMATCH')


def personal_context_action(tree,anchor,label):
    choices=[]
    for container in tree.iter('node'):
        children=list(container.iter('node'))
        if not any(n.get('package')==PACKAGE and anchor in (n.get('text',''),n.get('content-desc','')) for n in children):continue
        buttons=[n for n in children if n.get('package')==PACKAGE and n.get('class')=='android.widget.Button' and n.get('text')==label and n.get('clickable')=='true' and n.get('enabled')=='true']
        if len(buttons)==1:choices.append(buttons[0])
    unique={id(n):n for n in choices}
    if len(unique)!=1:raise NativeFailure('PERSONAL_CONTEXT_ACTION_AMBIGUOUS_NO_TAP')
    return next(iter(unique.values()))


def active_personal_context(tree,expected):
    for container in tree.iter('node'):
        text=' '.join(n.get('text','')+' '+n.get('content-desc','') for n in container.iter('node') if n.get('package')==PACKAGE)
        if 'Contexte actif' in text and set(re.findall(r'(?:INC|OTH)_[A-F0-9]{8}',text))=={expected}:return True
    return False


def context_filter_field(tree,identity):
    if not isinstance(identity,tuple) or len(identity)!=3 or not all(isinstance(v,str) and v for v in identity):raise NativeFailure('CONTEXT_FILTER_IDENTITY_REQUIRED_NO_INPUT')
    nodes=list(tree.iter('node'));text=' '.join(n.get('text','')+' '+n.get('content-desc','') for n in nodes if n.get('package')==PACKAGE)
    if any(v not in text for v in identity):raise NativeFailure('CONTEXT_FILTER_TICKET_NOT_CONFIRMED_NO_INPUT')
    labels=[i for i,n in enumerate(nodes) if n.get('package')==PACKAGE and 'Identifiant du commerce, PDV ou TPE' in (n.get('text','').strip(),n.get('content-desc','').strip())]
    ends=[i for i,n in enumerate(nodes) if n.get('package')==PACKAGE and n.get('class')=='android.widget.Button' and n.get('text')=='Appliquer les filtres']
    if not labels or len(ends)!=1 or min(labels)>=ends[0]:raise NativeFailure('CONTEXT_FILTER_REGION_REQUIRED_NO_INPUT')
    fields=[n for n in nodes[min(labels):ends[0]] if n.get('class')=='android.widget.EditText']
    if len(fields)!=1 or fields[0].get('package')!=PACKAGE or fields[0].get('password')!='false' or fields[0].get('enabled')!='true':raise NativeFailure('CONTEXT_FILTER_FIELD_AMBIGUOUS_NO_INPUT')
    return fields[0]


def confirmed_incident_reference(tree):
    references=set()
    for node in tree.iter('node'):
        if node.get('package')==PACKAGE:
            references.update(re.findall(r'Dépôt confirmé :\s*(INC-[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12})(?![a-f0-9-])',node.get('text','')+' '+node.get('content-desc','')))
    if len(references)!=1:raise NativeFailure('CONFIRMED_REFERENCE_NOT_UNIQUE')
    return references.pop()


def resolution_action_option(tree, label):
    allowed={'Répondre','Confirmer personnellement la résolution','Contester et réouvrir','Annuler avec motif'}
    lists=[node for node in tree.iter('node') if node.get('class')=='android.widget.ListView']
    if len(lists)!=1 or label not in allowed:raise NativeFailure('RESOLUTION_ACTION_PICKER_INVALID_NO_TAP')
    rows=[node for node in lists[0].iter('node') if node.get('class')=='android.widget.CheckedTextView']
    if not rows or any(node.get('text') not in allowed or node.get('package')!=PACKAGE or node.get('resource-id')!='android:id/text1' for node in rows):
        raise NativeFailure('RESOLUTION_ACTION_PICKER_INVALID_NO_TAP')
    choices=[node for node in rows if node.get('text')==label and node.get('enabled')=='true']
    if len(choices)!=1:raise NativeFailure('RESOLUTION_ACTION_AMBIGUOUS_NO_TAP')
    return choices[0]


class NativeUi:
    def __init__(self, adb, serial, avd, apk_sha, output, private_values=()):
        if serial != 'emulator-5586' or not re.fullmatch(r'FP_Incidents_[a-f0-9]{8}', avd):
            raise NativeFailure('OWNED_EMULATOR_REQUIRED')
        if not re.fullmatch(r'[a-f0-9]{64}', apk_sha):
            raise NativeFailure('EXPECTED_APK_DIGEST_REQUIRED')
        self.adb, self.serial, self.avd, self.apk_sha = str(adb), serial, avd, apk_sha
        self.output, self.private_values = Path(output), tuple(private_values)
        self.width, self.height, self.sequence = 720, 1280, 0
        self.remote = '/data/local/tmp/fp-incidents-ui-' + uuid.uuid4().hex
        self.created_remote = False
        self.system_recovery = False
        self.documents_mode = False
        self.camera_permission_mode = False
        self.delegated_focus_guard = None
        self.allow_resource_read_retry = False
        self.report = {'nativeUi': True, 'apiMocks': False, 'webViewDebuggingEnabledByTest': False,
                       'screenshotsOmittedBecauseFlagSecure': True, 'credentialsPersisted': False,
                       'serial': serial, 'package': PACKAGE, 'steps': [], 'depositAttempted': False,
                       'apkSHA256': apk_sha}

    def save(self):
        (self.output / 'native-result.json').write_text(json.dumps(self.report, indent=2), encoding='utf-8')

    def stage(self, code):
        self.report['currentAction'] = code
        self.save()

    def run(self, *args, private_stdin=None, timeout=25):
        if args[:2] == ('shell', 'input') or private_stdin is not None:
            self.focus()
        try:
            result = subprocess.run([self.adb, '-s', self.serial, *args], input=private_stdin,
                                    capture_output=True, timeout=timeout)
        except (OSError, subprocess.TimeoutExpired):
            raise NativeFailure('ADB_UNAVAILABLE_OR_TIMEOUT') from None
        if result.returncode:
            family=('HIERARCHY_DUMP' if args[:3]==('shell','uiautomator','dump') else
                    'HIERARCHY_READ' if args[:2]==('exec-out','cat') else
                    'WINDOW_FOCUS' if args[:3]==('shell','dumpsys','window') else
                    'TRANSIENT_REMOVE' if args[:3]==('shell','rm','-f') else
                    'TRANSIENT_DIRECTORY' if args[:2] in (('shell','mkdir'),('shell','rmdir')) else 'OTHER_FIXED_COMMAND')
            self.report['adbFailure']={'family':family,'exitCode':result.returncode,
                'permissionDenied':b'permission denied' in (result.stdout+result.stderr).lower(),
                'fileMissing':b'no such file' in (result.stdout+result.stderr).lower()}
            self.save()
            if args[:3] == ('shell', 'uiautomator', 'dump'):
                if result.returncode==137 and self.allow_resource_read_retry:
                    raise NativeFailure('HIERARCHY_RESOURCE_INTERRUPTED')
                message=(result.stdout+result.stderr).lower()
                if b'could not get idle state' in message or b'null root node' in message:
                    raise NativeFailure('HIERARCHY_TRANSITION_NOT_READY')
            raise NativeFailure('ADB_COMMAND_REFUSED')
        return result.stdout

    def focus(self, allow_system_dialog=False):
        raw=self.run('shell', 'dumpsys', 'window', timeout=12).decode('utf-8', errors='replace')
        if self.delegated_focus_guard is not None:self.delegated_focus_guard(raw)
        elif self.documents_mode:require_documents_foreground(raw)
        elif self.camera_permission_mode:require_camera_permission_foreground(raw)
        elif self.system_recovery and allow_system_dialog:require_system_recovery(raw)
        else:require_foreground(raw, allow_system_dialog)

    def validate_device(self, allow_system_dialog=False):
        names = self.run('emu', 'avd', 'name').decode('utf-8', errors='replace').splitlines()
        if self.avd not in names or self.run('shell', 'getprop', 'ro.kernel.qemu').strip() != b'1':
            raise NativeFailure('WRONG_EMULATOR_NO_INPUT')
        if self.run('shell', 'getprop', 'sys.boot_completed').strip() != b'1':
            raise NativeFailure('ANDROID_NOT_READY_NO_RESTART')
        sizes = re.findall(r'(?:Physical|Override) size:\s*(\d+)x(\d+)', self.run('shell', 'wm', 'size').decode())
        if not sizes:
            raise NativeFailure('DISPLAY_UNKNOWN')
        self.width, self.height = map(int, sizes[-1])
        if not (240 <= self.width <= 4000 and 400 <= self.height <= 5000):
            raise NativeFailure('DISPLAY_INVALID')
        apk = self.run('shell', 'pm', 'path', PACKAGE).decode().strip()
        if not re.fullmatch(r'package:/data/app/[A-Za-z0-9_./=+~-]+\.apk', apk):
            raise NativeFailure('INSTALLED_APK_PATH_INVALID')
        digest = self.run('shell', 'sha256sum', apk[len('package:'):]).decode().split()
        if not digest or digest[0] != self.apk_sha:
            raise NativeFailure('INSTALLED_APK_DIGEST_MISMATCH')
        self.focus(allow_system_dialog)
        self.run('shell', 'mkdir', '-m', '700', self.remote)
        self.created_remote = True

    def snapshot(self, label, allow_system_dialog=False):
        if allow_system_dialog:
            self.focus(True)
        else:
            self.focus()
        path = self.remote + '/hierarchy.xml'
        # Android can return an empty/truncated tree during an Ionic transition.
        # Repeat only this read, never the tap, login or deposit that preceded it.
        for attempt in range(3):
            self.focus(allow_system_dialog)
            try:
                self.run('shell', 'uiautomator', 'dump', path, timeout=35)
                raw = self.run('exec-out', 'cat', path, timeout=10).decode('utf-8', errors='replace')
                tree = parse_hierarchy(raw)
                break
            except NativeFailure as problem:
                if str(problem) not in ('HIERARCHY_INCOMPLETE', 'HIERARCHY_INVALID', 'HIERARCHY_TRANSITION_NOT_READY', 'HIERARCHY_RESOURCE_INTERRUPTED') or attempt == 2:
                    raise
                self.report['transientHierarchyReads'] = self.report.get('transientHierarchyReads', 0) + 1
                self.save()
                time.sleep(3 if str(problem)=='HIERARCHY_RESOURCE_INTERRUPTED' else .4)
            finally:
                self.run('shell', 'rm', '-f', path, timeout=10)
        self.sequence += 1
        safe_label = re.sub(r'[^A-Za-z0-9_-]', '_', label)[:70]
        filename = '%03d-%s.xml' % (self.sequence, safe_label)
        ET.ElementTree(redacted_hierarchy(tree, self.private_values)).write(self.output / filename, encoding='utf-8', xml_declaration=True)
        return tree

    def find(self, predicate, label, scroll=True):
        for attempt in range(7 if scroll else 1):
            tree = self.snapshot(label)
            candidates = [node for node in tree.iter('node') if predicate(node)]
            for node in candidates:
                if exposed_point(node, self.width, self.height):
                    return node
            if scroll and attempt < 6:
                def is_above(node):
                    left,top,right,bottom=bounds(node)
                    if right<=left or bottom<=top:
                        return context_scroll_up(tree,node)
                    return bottom<=self.height//4
                above = bool(candidates) and all(is_above(node) for node in candidates)
                top, bottom = round(self.height * .27), round(self.height * .82)
                self.run('shell', 'input', 'swipe', str(self.width // 2), str(top if above else bottom),
                         str(self.width // 2), str(bottom if above else top), '300')
        raise NativeFailure('CONTROL_NOT_FOUND')

    def tap(self, node):
        point = exposed_point(node, self.width, self.height)
        if point is None:
            raise NativeFailure('CONTROL_NOT_EXPOSED_NO_TAP')
        self.run('shell', 'input', 'tap', str(point[0]), str(point[1]))

    def tap_text(self, text, label, scroll=True):
        self.tap(self.find(lambda node: node.get('clickable') == 'true' and text in (node.get('text', ''), node.get('content-desc', '')), label, scroll))

    def wait_text(self, text, label, seconds=40):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            tree = self.snapshot(label)
            if any(text in (node.get('text', '') + ' ' + node.get('content-desc', '')) for node in tree.iter('node')):
                return tree
            if any('Identifiant ou mot de passe incorrect' in node.get('text', '') for node in tree.iter('node')):
                raise NativeFailure('AUTHENTICATION_REFUSED_NO_RETRY')
            time.sleep(.4)
        raise NativeFailure('EXPECTED_SCREEN_NOT_CONFIRMED')

    def fill(self, node, value, incident_index=None, conversation_identity=None, draft_identity=None,context_filter_identity=None):
        self.tap(node)
        # A WebView tap returns before Ionic/IME has focused the real input.
        # Wait for that exact resource, not just the application window, before
        # injecting any character. Reading does not repeat the tap or input.
        focused=self.focused_input(node,incident_index,conversation_identity,draft_identity,context_filter_identity)
        command = paced_private_input_command(value, clear_existing=focused.get('text') != '')
        self.run('shell', 'sh', private_stdin=command)
        ime = self.run('shell', 'dumpsys', 'input_method').decode('utf-8', errors='replace')
        if re.search(r'(?:mInputShown|isInputViewShown)\s*=\s*true', ime):
            self.run('shell', 'input', 'keyevent', '4')

    def focused_input(self, expected, incident_index=None, conversation_identity=None, draft_identity=None,context_filter_identity=None):
        resource=expected.get('resource-id','')
        reply_context=(isinstance(conversation_identity,tuple) and len(conversation_identity)==2 and
                       all(isinstance(value,str) and value for value in conversation_identity))
        filter_context=(isinstance(context_filter_identity,tuple) and len(context_filter_identity)==3 and all(isinstance(value,str) and value for value in context_filter_identity))
        if expected.get('class')!='android.widget.EditText' or expected.get('package')!=PACKAGE or (not resource and not reply_context and not filter_context and (type(incident_index) is not int or incident_index not in (0,1))):
            raise NativeFailure('INPUT_IDENTITY_UNCONFIRMED_NO_INPUT')
        for attempt in range(3):
            tree=self.snapshot('focused-input')
            inputs=[n for n in tree.iter('node') if n.get('class')=='android.widget.EditText' and n.get('focused')=='true']
            if len(inputs)==1 and inputs[0].get('resource-id','')==resource and inputs[0].get('package')==PACKAGE and inputs[0].get('password')==expected.get('password') and inputs[0].get('enabled')=='true':
                if filter_context:
                    if context_filter_field(tree,context_filter_identity) is inputs[0]:return inputs[0]
                    raise NativeFailure('CONTEXT_FILTER_FOCUS_NOT_CONFIRMED_NO_INPUT')
                if resource:return inputs[0]
                if reply_context:
                    if conversation_input(tree,*conversation_identity) is inputs[0]:return inputs[0]
                if draft_identity is not None:
                    if (type(incident_index) is int and incident_index in (0,1) and isinstance(draft_identity,tuple) and
                            len(draft_identity)==2 and draft_edit_fields(tree,*draft_identity)[incident_index] is inputs[0]):return inputs[0]
                    raise NativeFailure('DRAFT_FOCUS_NOT_CONFIRMED_NO_INPUT')
                # Angular's plain input/textarea expose no Android resource-id.
                # Use their position ONLY inside this exact two-field creation
                # form; the login path never enables this structural selector.
                fields=[n for n in tree.iter('node') if n.get('class')=='android.widget.EditText']
                labels={n.get('text','') for n in tree.iter('node')}
                if len(fields)==2 and {'Nouvelle déclaration','Objet','Description'}.issubset(labels) and fields[incident_index] is inputs[0] and all(n.get('package')==PACKAGE and n.get('password')=='false' for n in fields):
                    return inputs[0]
            if attempt<2: time.sleep(.3)
        raise NativeFailure('INPUT_FOCUS_NOT_CONFIRMED_NO_INPUT')

    def prepare_login_input(self, fixture):
        self.stage('LOGIN_FORM')
        tree = self.snapshot('initial')
        if any(node.get('content-desc') == 'Ouvrir le menu' or node.get('text') == 'Ouvrir le menu' for node in tree.iter('node')):
            self.tap_text('Ouvrir le menu', 'old-session-menu', False)
            self.tap_text('Se déconnecter', 'old-session-logout')
        tree = self.wait_text('Utilisez votre compte existant', 'login-form')
        user,password = login_field_nodes(tree)
        self.stage('FILL_LOGIN')
        resource=user.get('resource-id')
        user=self.find(lambda node: node.get('class')=='android.widget.EditText' and node.get('resource-id')==resource and node.get('password')=='false','login-field')
        self.fill(user, fixture['login'])
        # Re-read coordinates after the IME may have resized the WebView.
        password = self.find(lambda node: node.get('class') == 'android.widget.EditText' and node.get('password') == 'true', 'password-field')
        self.stage('FILL_PASSWORD');self.fill(password, fixture['password'])
        self.stage('VERIFY_LOGIN_INPUT')
        self.confirm_login_input(fixture)

    def login(self, fixture):
        self.prepare_login_input(fixture)
        self.stage('LOGIN_SEND');self.tap_text('Se connecter', 'login-send')
        self.stage('WAIT_AUTHENTICATED_HOME')
        self.wait_text('Bonjour ' + fixture['login'], 'authenticated-home')

    def confirm_login_input(self, fixture):
        # IME/accessibility may expose the previous text for one frame. Read
        # again only; no retyping, repeated authentication or weaker check.
        for attempt in range(3):
            summary = login_input_summary(self.snapshot('login-input-check'), fixture)
            self.report['loginInputCheck'] = summary
            self.save()
            if all(summary.values()):
                return
            if attempt < 2:
                time.sleep(.5)
        raise NativeFailure('LOGIN_INPUT_NOT_CONFIRMED_NO_SEND')

    def declare(self, fixture):
        self.login(fixture)
        self.stage('OPEN_INCIDENTS')
        self.tap_text('Ouvrir le menu', 'menu', False)
        self.tap_text('Assistance · Incidents', 'incidents-menu')
        self.wait_text('Assistance · Incidents', 'incidents-view')
        self.tap_text('Déclarer un incident', 'new-incident')
        self.stage('CHOOSE_CONTEXT')
        self.open_creation_context()
        self.tap(context_option(self.snapshot('synthetic-context'),fixture.get('contextLabel','Synthetic commerce')))
        self.fill_incident_fields(fixture['subject'],'Declaration depuis APK installee, API et base reelles.')
        self.stage('VERIFY_INCIDENT_INPUT')
        self.confirm_incident_input(fixture['subject'],'Declaration depuis APK installee, API et base reelles.')
        submit = self.find(lambda node: node.get('text') == 'Envoyer' and node.get('class') == 'android.widget.Button', 'deposit-send')
        self.report['depositAttempted'] = True
        self.stage('SEND_DEPOSIT_ONCE')
        self.save()  # A timeout after this point is uncertain, never an automatic second send.
        self.tap(submit)
        self.stage('WAIT_DEPOSIT_CONFIRMATION')
        self.wait_text('Dépôt confirmé :', 'deposit-confirmed')
        self.report['steps'].append('NATIVE_DECLARATION_OK')

    def replay_deposit(self, fixture):
        description='Declaration de recette avec accuse incertain et reprise du meme contenu.'
        self.login(fixture)
        self.tap_text('Ouvrir le menu','replay-menu',False)
        self.tap_text('Assistance · Incidents','replay-assistance')
        self.wait_text('Assistance · Incidents','replay-list')
        self.tap_text('Déclarer un incident','replay-new')
        self.open_creation_context();self.tap(context_option(self.snapshot('replay-context'),fixture.get('contextLabel','Synthetic commerce')))
        self.fill_replay_fields(fixture['subject'],description)
        self.confirm_incident_input(fixture['subject'],description)
        self.report['depositAttempted']=True;self.stage('SEND_FIRST_DEPOSIT_ONCE')
        self.tap(self.owned_button('Envoyer','replay-first-send'))
        self.wait_text('Envoi non confirmé. Ne créez pas un second dépôt','replay-uncertain-result')
        self.report['uncertainResultRead']=True;self.save()
        # Only this explicit recovery test retries, once, after reading the
        # uncertain result and confirming that neither field has changed.
        self.confirm_incident_input(fixture['subject'],description)
        self.report['sameInputConfirmedBeforeRetry']=True;self.stage('MANUAL_EXACT_RETRY_ONCE')
        self.tap(self.owned_button('Envoyer','replay-exact-send'))
        tree=self.wait_text('Dépôt confirmé :','replay-confirmed')
        self.report.update(reference=confirmed_incident_reference(tree),manualRetryConfirmed=True)
        self.report['steps'].append('NATIVE_LOST_ACK_MANUAL_EXACT_RETRY_OK')

    def fill_replay_fields(self,subject,description):
        for index,value in enumerate((subject,description)):
            for attempt in range(7):
                tree=self.snapshot('replay-fields');field=invalid_incident_fields(tree)[index]
                if exposed_point(field,self.width,self.height):break
                if attempt==6:raise NativeFailure('REPLAY_FIELD_NOT_EXPOSED_NO_INPUT')
                up=context_scroll_up(tree,field);top,bottom=round(self.height*.27),round(self.height*.82)
                self.run('shell','input','swipe',str(self.width//2),str(top if up else bottom),str(self.width//2),str(bottom if up else top),'300')
            self.stage('FILL_REPLAY_SUBJECT' if index==0 else 'FILL_REPLAY_DESCRIPTION')
            self.fill(field,value,incident_index=index)

    def required_other(self, fixture):
        self.login(fixture)
        self.stage('OPEN_REQUIRED_OTHER_FORM')
        self.tap_text('Ouvrir le menu','required-menu',False)
        self.tap_text('Assistance · Incidents','required-assistance')
        self.wait_text('Assistance · Incidents','required-list')
        self.tap_text('Déclarer un incident','required-new')
        self.open_creation_context();self.tap(context_option(self.snapshot('required-context')))
        # Two distinct invalid forms, neither is replay of an uncertain deposit.
        # Read both field values before each attempt; never guess which input.
        for attempt in range(2):
            tree=self.snapshot('required-empty-description');fields=invalid_incident_fields(tree)
            if attempt==0 and fields[0].get('text',''):raise NativeFailure('INITIAL_FORM_NOT_EMPTY_NO_SEND')
            if attempt==1:
                for scroll in range(7):
                    fields=invalid_incident_fields(tree)
                    if exposed_point(fields[0],self.width,self.height):break
                    if scroll==6:raise NativeFailure('REQUIRED_SUBJECT_NOT_EXPOSED_NO_INPUT')
                    up=context_scroll_up(tree,fields[0]);top,bottom=round(self.height*.27),round(self.height*.82)
                    self.run('shell','input','swipe',str(self.width//2),str(top if up else bottom),str(self.width//2),str(bottom if up else top),'300')
                    tree=self.snapshot('required-subject-position')
                self.fill(fields[0],fixture['subject'],incident_index=0)
                tree=self.snapshot('required-subject-only');fields=invalid_incident_fields(tree)
                if fields[0].get('text')!=fixture['subject']:raise NativeFailure('REQUIRED_SUBJECT_NOT_CONFIRMED_NO_SEND')
            send=self.owned_button('Envoyer','required-invalid-send')
            self.stage('INVALID_EMPTY_FORM_ONCE' if attempt==0 else 'INVALID_DESCRIPTION_MISSING_ONCE')
            self.tap(send)
            tree=self.wait_text('Choisissez un dossier, puis renseignez l’objet et la description.','required-error-explicit')
            invalid_incident_fields(tree)
            self.report['invalidFormAttempts']=attempt+1;self.report['requiredErrorConfirmed']=True;self.save()
        # Explicitly select OTHER, without asking for a business reference.
        for attempt in range(7):
            tree=self.snapshot('other-category-control');control=creation_category_control(tree)
            if exposed_point(control,self.width,self.height):break
            if attempt==6:raise NativeFailure('CATEGORY_NOT_EXPOSED_NO_TAP')
            up=context_scroll_up(tree,control);top,bottom=round(self.height*.27),round(self.height*.82)
            self.run('shell','input','swipe',str(self.width//2),str(top if up else bottom),str(self.width//2),str(bottom if up else top),'300')
        self.tap(control);self.tap(other_category_option(self.snapshot('other-category-picker')))
        description='Description libre sans code ni reference metier depuis Android.'
        self.fill_incident_fields(fixture['subject'],description);self.confirm_incident_input(fixture['subject'],description)
        send=self.owned_button('Envoyer','other-valid-send')
        self.report['otherCategorySelected']=True;self.report['depositAttempted']=True;self.stage('SEND_OTHER_WITHOUT_REFERENCE_ONCE')
        self.tap(send);self.wait_text('Dépôt confirmé :','other-deposit-confirmed')
        self.report['steps'].append('NATIVE_REQUIRED_REJECTED_OTHER_DECLARED')

    def conversation(self, fixture, public_key='supportReply'):
        states=conversation_state_labels(fixture)
        forbidden=conversation_forbidden_texts(fixture)
        service_texts=conversation_service_texts(fixture)
        self.login(fixture)
        self.stage('OPEN_CONVERSATION_LIST')
        self.tap_text('Ouvrir le menu','conversation-menu',False)
        self.tap_text('Assistance · Incidents','conversation-assistance')
        require_conversation_privacy(self.wait_text('Assistance · Incidents','conversation-list'),forbidden)
        def owned_card(node):
            text=' '.join(child.get('text','')+' '+child.get('content-desc','') for child in node.iter('node'))
            return (node.get('class')=='android.widget.Button' and node.get('package')==PACKAGE and
                    node.get('clickable')=='true' and fixture['reference'] in text and fixture['subject'] in text)
        self.tap(self.find(owned_card,'same-ticket-card'))
        if 'expectedStateLabel' in states:
            self.wait_text(fixture['reference']+' · '+states['expectedStateLabel'],'conversation-initial-state')
            self.report['expectedInitialStateRead']=True;self.save()
        self.stage('READ_COMMERCIAL_PUBLIC_REPLY' if public_key=='publicReply' else 'READ_PORTAL_SUPPORT_REPLY')
        tree=self.wait_text(fixture[public_key],'public-reply-visible' if public_key=='publicReply' else 'support-reply-visible')
        conversation_input(tree,fixture['reference'],fixture['subject'])
        require_conversation_privacy(tree,forbidden)
        if public_key=='publicReply':
            self.find(lambda node:node.get('package')==PACKAGE and (node.get('text','')+' '+node.get('content-desc','')).startswith(fixture['publicAuthor']+' ·'),'commercial-message-author')
            self.report['commercialMessageAuthorRead']=True;self.stage('READ_COMMERCIAL_CLEAN_IMAGE')
            self.find(lambda node:node.get('package')==PACKAGE and 'image/png · CLEAN' in (node.get('text','')+' '+node.get('content-desc','')),'commercial-clean-image')
            tree=self.snapshot('commercial-public-attachment');conversation_input(tree,fixture['reference'],fixture['subject'])
            self.report['publicCleanImageRead']=True;self.save()
        for index,text in enumerate(service_texts):
            self.stage('READ_CONFIGURED_SERVICE_LEVELS')
            require_conversation_privacy(self.wait_text(text,'service-level-'+str(index)),forbidden)
        if service_texts:
            self.report['serviceLevelTextsRead']=len(service_texts);self.save()
        if any(fixture['internalNote'] in (node.get('text','')+' '+node.get('content-desc','')) for node in tree.iter('node')):
            raise NativeFailure('INTERNAL_NOTE_EXPOSED_NO_REPLY')
        self.report.update(internalNoteAbsent=True,reference=fixture['reference'])
        self.report['commercialReplyRead' if public_key=='publicReply' else 'supportReplyRead']=True
        for attempt in range(7):
            tree=self.snapshot('conversation-reply-field')
            field=conversation_input(tree,fixture['reference'],fixture['subject'])
            if exposed_point(field,self.width,self.height):break
            if attempt==6:raise NativeFailure('CONVERSATION_FIELD_NOT_EXPOSED_NO_INPUT')
            up=context_scroll_up(tree,field);top,bottom=round(self.height*.27),round(self.height*.82)
            self.run('shell','input','swipe',str(self.width//2),str(top if up else bottom),str(self.width//2),str(bottom if up else top),'300')
        self.stage('FILL_CONVERSATION_REPLY')
        identity=(fixture['reference'],fixture['subject'])
        self.fill(field,fixture['reply'],conversation_identity=identity)
        for attempt in range(3):
            field=conversation_input(self.snapshot('reply-input-check'),*identity)
            matched=field.get('text')==fixture['reply']
            self.report['replyInputMatches']=matched;self.save()
            if matched:break
            if attempt==2:raise NativeFailure('REPLY_INPUT_NOT_CONFIRMED_NO_SEND')
            time.sleep(.5)
        submit=self.find(lambda node:node.get('class')=='android.widget.Button' and node.get('text')=='Répondre' and node.get('package')==PACKAGE,'reply-send')
        self.report['replyAttempted']=True;self.stage('SEND_REPLY_ONCE')
        self.tap(submit)
        self.stage('WAIT_REPLY_CONFIRMATION')
        self.wait_text('Action enregistrée.','reply-confirmed')
        require_conversation_privacy(self.wait_text(fixture['reply'],'same-reply-visible'),forbidden)
        if forbidden:self.report['confidentialContextsAbsent']=True;self.save()
        if 'expectedReplyStateLabel' in states:
            self.wait_text(fixture['reference']+' · '+states['expectedReplyStateLabel'],'conversation-reply-state')
            self.report['expectedReplyStateRead']=True;self.save()
        self.report['steps'].append('NATIVE_COMMERCIAL_CONVERSATION_OK' if public_key=='publicReply' else 'NATIVE_CONVERSATION_OK')

    def context_switch(self,fixture):
        self.login(fixture)
        def menu(label):
            self.tap_text('Ouvrir le menu','context-menu',False)
            if label=='Mes rattachements':
                tree=self.snapshot('context-menu-section')
                if not any(label in (n.get('text'),n.get('content-desc')) and exposed_point(n,self.width,self.height) for n in tree.iter('node')):
                    self.tap_text('Mon commerce','context-expand-owned-merchant-section',False)
            self.tap_text(label,'context-menu-destination');self.wait_text(label,'context-page')
        def absence(tree,items):
            require_conversation_privacy(tree,[item[key] for item in items for key in ('subject','reference','publicReply')])
        def open_ticket(item):
            self.tap(self.find(lambda n:n.get('class')=='android.widget.Button' and n.get('package')==PACKAGE and n.get('clickable')=='true'
                and item['reference'] in ' '.join(c.get('text','') for c in n.iter('node')) and item['subject'] in ' '.join(c.get('text','') for c in n.iter('node')),'context-same-ticket-card'))
            return self.wait_text(item['publicReply'],'context-public-message')
        def anchored_action(anchor,label):
            for attempt in range(7):
                tree=self.snapshot('context-anchored-action');control=personal_context_action(tree,anchor,label)
                if exposed_point(control,self.width,self.height):self.tap(control);return
                if attempt==6:raise NativeFailure('PERSONAL_CONTEXT_ACTION_NOT_EXPOSED_NO_TAP')
                up=context_scroll_up(tree,control);top,bottom=round(self.height*.27),round(self.height*.82)
                self.run('shell','input','swipe',str(self.width//2),str(top if up else bottom),str(self.width//2),str(bottom if up else top),'300')
        def select_context(code):
            self.stage('SELECT_REAL_PERSONAL_CONTEXT');anchored_action(code,'Utiliser ce contexte')
            deadline=time.monotonic()+30
            while time.monotonic()<deadline:
                if active_personal_context(self.snapshot('active-personal-context'),code):return
                time.sleep(.4)
            raise NativeFailure('PERSONAL_CONTEXT_CHANGE_NOT_CONFIRMED')
        def filter_point(point):
            # Android exposes the HTML select's accessible label, not its
            # selected option text. The active code was verified on the real
            # personal-context page; bind this input to the exact target ticket
            # AND its real public message instead of an invisible option label.
            identity=(fixture['first']['reference'],fixture['first']['subject'],fixture['first']['publicReply'])
            for attempt in range(7):
                tree=self.snapshot('context-filter-field');field=context_filter_field(tree,identity)
                if exposed_point(field,self.width,self.height):break
                if attempt==6:raise NativeFailure('CONTEXT_FILTER_NOT_EXPOSED_NO_INPUT')
                up=context_scroll_up(tree,field);top,bottom=round(self.height*.27),round(self.height*.82)
                self.run('shell','input','swipe',str(self.width//2),str(top if up else bottom),str(self.width//2),str(bottom if up else top),'300')
            self.fill(field,point,context_filter_identity=identity)
            if context_filter_field(self.snapshot('context-filter-input-confirmed'),identity).get('text')!=point:raise NativeFailure('CONTEXT_FILTER_INPUT_NOT_CONFIRMED_NO_ACTION')
            self.tap_text('Appliquer les filtres','context-filter-apply')
        menu('Assistance · Incidents');open_ticket(fixture['source']);self.report['homeConversationRead']=True;self.save()
        menu('Mes rattachements');anchored_action(fixture['associationLabel'],'Examiner et accepter');self.wait_text('Confirmer votre rattachement','context-accept-confirmation')
        self.stage('ACCEPT_PENDING_PERSONAL_ASSOCIATION_ONCE');self.tap_text('Confirmer le rattachement','context-accept-once');self.wait_text('Rattachement accepté. Vos autres droits sont conservés.','context-accepted');self.report['personalAssociationAccepted']=True;self.save()
        select_context(fixture['toCode']);menu('Assistance · Incidents');self.wait_text(fixture['first']['subject'],'target-acquirer-list');absence(self.snapshot('old-context-absent'),[fixture['source']]);self.report['previousAcquirerContentAbsent']=True
        open_ticket(fixture['first']);self.tap_text('Filtres de suivi','context-filter-panel');filter_point(fixture['pdvA']);self.wait_text('1 incident(s) dans votre périmètre.','context-pdv-a-single');self.wait_text(fixture['first']['publicReply'],'same-pdv-a-message-reloaded');self.report['pdvIncludesTerminalRead']=True;self.save()
        filter_point(fixture['pdvB']);self.wait_text(fixture['second']['subject'],'context-pdv-b-list');absence(self.snapshot('previous-pdv-absent'),[fixture['source'],fixture['first']]);absence(open_ticket(fixture['second']),[fixture['source'],fixture['first']]);self.report['previousPdvContentAbsent']=True;self.save()
        menu('Mes rattachements');select_context(fixture['fromCode']);menu('Assistance · Incidents');self.wait_text(fixture['source']['subject'],'home-acquirer-list');absence(open_ticket(fixture['source']),[fixture['first'],fixture['second']]);self.report['returnedHomeWithoutTargetContent']=True;self.report['reference']=fixture['source']['reference'];self.report['steps'].append('NATIVE_CONTEXT_SWITCH_AND_PDV_FILTER_OK');self.save()

    def revocation(self, fixture):
        self.login(fixture)
        self.tap_text('Ouvrir le menu','revocation-menu',False)
        self.tap_text('Assistance · Incidents','revocation-assistance')
        self.wait_text('Assistance · Incidents','revocation-list')
        self.tap(self.find(lambda node:node.get('class')=='android.widget.Button' and node.get('package')==PACKAGE and
                          node.get('clickable')=='true' and fixture['reference'] in ' '.join(n.get('text','')+' '+n.get('content-desc','') for n in node.iter('node')) and
                          fixture['subject'] in ' '.join(n.get('text','')+' '+n.get('content-desc','') for n in node.iter('node')),'revocation-ticket'))
        tree=self.wait_text(fixture['supportReply'],'revocation-public-reply')
        conversation_input(tree,fixture['reference'],fixture['subject'])
        self.report.update(reference=fixture['reference'],supportReplyRead=True)
        # Java observes this new owned report, then uses the real admin API.
        # The native driver changes no permissions; all refreshes are read-only.
        self.stage('WAIT_LIVE_REVOCATION')
        deadline=time.monotonic()+90
        while time.monotonic()<deadline:
            self.tap(self.owned_button('Actualiser','revocation-refresh'))
            try:
                tree=self.wait_text('0 incident(s) dans votre périmètre.','revocation-empty-list',seconds=10)
                require_revoked_conversation_absent(tree,fixture)
                break
            except NativeFailure as problem:
                if str(problem)!='EXPECTED_SCREEN_NOT_CONFIRMED':raise
        else:raise NativeFailure('LIVE_REVOCATION_NOT_REFLECTED')
        self.report['revokedConversationCleared']=True;self.save()
        self.stage('LOGOUT_AFTER_LIVE_REVOCATION')
        self.tap_text('Ouvrir le menu','revoked-logout-menu',False)
        self.tap_text('Se déconnecter','revoked-logout')
        tree=self.wait_text('Utilisez votre compte existant','revoked-login-screen')
        if any(fixture['supportReply'] in node.get('text','') for node in tree.iter('node')):
            raise NativeFailure('REVOKED_CONTENT_VISIBLE_AFTER_LOGOUT')
        self.report['loggedOutToLogin']=True;self.save()
        self.login(fixture)
        self.tap_text('Ouvrir le menu','revoked-relogin-menu',False)
        self.tap_text('Assistance · Incidents','revoked-relogin-assistance')
        self.tap(self.owned_button('Actualiser','revoked-relogin-refresh'))
        tree=self.wait_text('0 incident(s) dans votre périmètre.','revoked-relogin-empty')
        require_revoked_conversation_absent(tree,fixture)
        self.report['reloginDidNotRestoreRights']=True
        self.report['steps'].append('NATIVE_LIVE_REVOCATION_LOGOUT_RELOGIN_OK')

    def resolution(self, fixture):
        self.login(fixture)
        self.stage('OPEN_RESOLVED_TICKET')
        self.tap_text('Ouvrir le menu','resolution-menu',False)
        self.tap_text('Assistance · Incidents','resolution-assistance')
        self.wait_text('Assistance · Incidents','resolution-list')
        self.tap(self.find(lambda node:node.get('class')=='android.widget.Button' and node.get('package')==PACKAGE and node.get('clickable')=='true' and
                          fixture['reference'] in node.get('text','') and fixture['subject'] in node.get('text',''),'same-resolved-card'))
        self.wait_text(fixture['reference']+' · Résolu','resolution-read')
        self.wait_text(fixture['resolutionText'],'resolution-public-text')
        identity=(fixture['reference'],fixture['subject'])
        for label,state,key in [('Confirmer personnellement la résolution','Clôturé','confirmationAttempted'),('Contester et réouvrir','En cours','reopenAttempted')]:
            for attempt in range(7):
                tree=self.snapshot('resolution-action-control')
                control=ticket_action_control(tree,*identity,{'Action','Répondre','Confirmer personnellement la résolution','Contester et réouvrir','Annuler avec motif'})
                if exposed_point(control,self.width,self.height):break
                if attempt==6:raise NativeFailure('RESOLUTION_ACTION_NOT_EXPOSED_NO_TAP')
                up=context_scroll_up(tree,control);top,bottom=round(self.height*.27),round(self.height*.82)
                self.run('shell','input','swipe',str(self.width//2),str(top if up else bottom),str(self.width//2),str(bottom if up else top),'300')
            self.tap(control);self.tap(resolution_action_option(self.snapshot('resolution-action-picker'),label))
            if key=='reopenAttempted':
                for attempt in range(7):
                    tree=self.snapshot('resolution-reopen-motive');field=conversation_input(tree,*identity)
                    if exposed_point(field,self.width,self.height):break
                    if attempt==6:raise NativeFailure('RESOLUTION_MOTIVE_NOT_EXPOSED_NO_INPUT')
                    up=context_scroll_up(tree,field);top,bottom=round(self.height*.27),round(self.height*.82)
                    self.run('shell','input','swipe',str(self.width//2),str(top if up else bottom),str(self.width//2),str(bottom if up else top),'300')
                self.fill(field,fixture['reopenText'],conversation_identity=identity)
                if conversation_input(self.snapshot('resolution-motive-check'),*identity).get('text')!=fixture['reopenText']:
                    raise NativeFailure('RESOLUTION_MOTIVE_NOT_CONFIRMED_NO_SEND')
            button=self.owned_button(label,'resolution-submit')
            if self.report.get(key):raise NativeFailure('RESOLUTION_UNCERTAIN_ACTION_NOT_REPLAYED')
            self.report[key]=True;self.stage('CONFIRM_RESOLUTION_ONCE' if key=='confirmationAttempted' else 'REOPEN_WITH_MOTIVE_ONCE')
            self.tap(button)
            self.wait_text(fixture['reference']+' · '+state,'resolution-new-state')
            self.report['confirmedClosed' if key=='confirmationAttempted' else 'confirmedReopened']=True;self.save()
        self.tap_text('Historique autorisé','resolution-open-history')
        self.tap(self.owned_button('Charger l’historique','resolution-load-history'))
        self.wait_text(fixture['reopenText'],'resolution-reopen-history')
        self.report['reference']=fixture['reference'];self.report['steps'].append('NATIVE_RESOLUTION_CONFIRMED_AND_REOPENED')

    def owned_button(self,label,stage):
        return self.find(lambda node:node.get('class')=='android.widget.Button' and node.get('package')==PACKAGE and node.get('clickable')=='true' and node.get('text')==label,stage)

    def wait_focus_owner(self, documents, seconds=35):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            raw = self.run('shell', 'dumpsys', 'window', timeout=12).decode('utf-8', errors='replace')
            try:
                if documents: require_documents_foreground(raw)
                else: require_foreground(raw)
                self.documents_mode = documents
                self.camera_permission_mode = False
                return
            except NativeFailure:
                time.sleep(.4)  # Read only while an already requested activity transition completes.
        raise NativeFailure('DOCUMENT_TRANSITION_NOT_CONFIRMED_NO_INPUT')

    def export_attachment(self, fixture, item, cancel=False):
        filename = attachment_export_name(item)
        remote = '/sdcard/Download/' + filename
        if self.run('shell', 'if [ -e ' + shlex.quote(remote) + ' ]; then echo EXISTS; else echo ABSENT; fi').strip() != b'ABSENT':
            raise NativeFailure('OWNED_EXPORT_NAME_ALREADY_EXISTS_NO_ACTION')
        button = None
        for attempt in range(9):
            tree = self.snapshot('attachment-row')
            button = attachment_download_control(tree, item['contentType'], fixture['reference'], fixture['subject'])
            if exposed_point(button, self.width, self.height): break
            if attempt == 8: raise NativeFailure('ATTACHMENT_NOT_EXPOSED_NO_TAP')
            up = context_scroll_up(tree, button); top, bottom = round(self.height*.27), round(self.height*.82)
            self.run('shell','input','swipe',str(self.width//2),str(top if up else bottom),str(self.width//2),str(bottom if up else top),'300')
        exports = self.report.setdefault('exports', [])
        record = {'contentType': item['contentType'], 'cancelRequested': cancel, 'requestAttempted': True}
        exports.append(record); self.stage('REQUEST_NATIVE_DOCUMENT_COPY_ONCE')
        self.tap(button)
        self.wait_focus_owner(True)
        tree = self.snapshot('document-save-picker')
        save = document_save_controls(tree, filename)
        record['pickerFilenameConfirmed'] = True
        if cancel:self.dismiss_document_ime(filename,record)
        self.stage('CANCEL_DOCUMENT_COPY_ONCE' if cancel else 'CONFIRM_DOCUMENT_SAVE_ONCE')
        if cancel: self.cancel_document_picker(filename,record)
        else: self.tap(save)
        self.wait_focus_owner(False)
        if cancel:
            self.wait_text('Enregistrement annulé. Le dossier reste disponible.', 'document-cancel-confirmed')
            absent = self.run('shell','if [ -e '+shlex.quote(remote)+' ]; then echo EXISTS; else echo ABSENT; fi').strip() == b'ABSENT'
            if not absent: raise NativeFailure('CANCELLED_EXPORT_CREATED_A_FILE')
            record['cancelConfirmed'] = True; record['noFileCreated'] = True
        else:
            self.wait_text('Copie enregistrée.', 'document-saved-confirmed')
            digest = self.run('shell', 'sha256sum', remote).decode('ascii').split()
            if len(digest) < 2 or digest[0] != item['sha256']:
                raise NativeFailure('NATIVE_SAVED_FILE_CONTENT_NOT_CONFIRMED')
            record['savedAcknowledged'] = True; record['savedContentMatches'] = True
        self.save()

    def cancel_document_picker(self,filename,record):
        # Back may first leave an inner document-picker state. At most two
        # navigation steps, never another download, Save or input in our app.
        for attempt in range(2):
            if attempt:
                raw=self.run('shell','dumpsys','window').decode('utf-8',errors='replace')
                try:
                    require_foreground(raw);self.documents_mode=False;return
                except NativeFailure:require_documents_foreground(raw)
            self.focus()
            document_save_controls(self.snapshot('same-picker-before-cancel'),filename)
            record['cancelBackCount']=attempt+1
            self.stage('CANCEL_DOCUMENT_BACK_ONCE' if attempt==0 else 'COMPLETE_SAME_PICKER_CANCELLATION_ONCE')
            self.run('shell','input','keyevent','4')
            try:
                self.wait_focus_owner(False,seconds=8 if attempt==0 else 35);return
            except NativeFailure:
                if attempt:raise

    def dismiss_document_ime(self,filename,record):
        self.focus()  # Only the exact delegated document activity is allowed.
        visible=document_ime_visible(self.run('shell','dumpsys','input_method').decode('utf-8',errors='replace'))
        record['keyboardVisibleBeforeCancel']=visible;self.save()
        if not visible:return
        self.stage('HIDE_DOCUMENT_KEYBOARD_ONCE')
        self.run('shell','input','keyevent','4')
        stable=0
        for attempt in range(12):
            self.focus()  # Never send a second Back into a changed/unknown window.
            hidden=not document_ime_visible(self.run('shell','dumpsys','input_method').decode('utf-8',errors='replace'))
            stable=stable+1 if hidden else 0
            if stable>=2:
                document_save_controls(self.snapshot('same-picker-keyboard-hidden'),filename)
                record['keyboardDismissedBeforeCancel']=True;self.save();return
            time.sleep(.4)
        raise NativeFailure('DOCUMENT_KEYBOARD_NOT_HIDDEN_NO_CANCEL')

    def attachments(self, fixture):
        self.login(fixture)
        self.stage('OPEN_TICKET_WITH_REAL_SCANNED_ATTACHMENTS')
        self.tap_text('Ouvrir le menu','attachments-menu',False)
        self.tap_text('Assistance · Incidents','attachments-assistance')
        self.wait_text('Assistance · Incidents','attachments-list')
        self.tap(self.find(lambda node:node.get('class')=='android.widget.Button' and node.get('package')==PACKAGE and node.get('clickable')=='true' and
                          fixture['reference'] in node.get('text','') and fixture['subject'] in node.get('text',''),'attachments-ticket-card'))
        require_ticket(self.wait_text('Pièces jointes', 'attachments-opened'), fixture['reference'], fixture['subject'])
        self.report['reference'] = fixture['reference']
        self.export_attachment(fixture, fixture['files'][0], cancel=True)
        for item in fixture['files']: self.export_attachment(fixture, item)
        self.report['steps'].append('NATIVE_ATTACHMENTS_SAVED_AND_CANCELLED')

    def photo_refuse_declare(self, fixture):
        self.login(fixture)
        self.stage('OPEN_OWNED_PHOTO_TICKET')
        self.tap_text('Ouvrir le menu','photo-menu',False)
        self.tap_text('Assistance · Incidents','photo-assistance')
        self.wait_text('Assistance · Incidents','photo-list')
        self.tap(self.find(lambda node:node.get('class')=='android.widget.Button' and node.get('package')==PACKAGE and node.get('clickable')=='true' and
                          fixture['reference'] in node.get('text','') and fixture['subject'] in node.get('text',''),'photo-ticket-card'))
        self.wait_text('Pièces jointes','photo-ticket-opened')
        for attempt in range(9):
            tree=self.snapshot('photo-capture-control');control=photo_capture_control(tree,fixture['reference'],fixture['subject'])
            if exposed_point(control,self.width,self.height):break
            if attempt==8:raise NativeFailure('PHOTO_CAPTURE_NOT_EXPOSED_NO_TAP')
            up=context_scroll_up(tree,control);top,bottom=round(self.height*.27),round(self.height*.82)
            self.run('shell','input','swipe',str(self.width//2),str(top if up else bottom),str(self.width//2),str(bottom if up else top),'300')
        self.report['photoRequestedOnce']=True;self.stage('REQUEST_OPTIONAL_PHOTO_ONCE');self.tap(control)
        deadline=time.monotonic()+30
        while True:
            raw=self.run('shell','dumpsys','window',timeout=12).decode('utf-8',errors='replace')
            try:require_camera_permission_foreground(raw);break
            except NativeFailure:
                if time.monotonic()>=deadline:raise NativeFailure('CAMERA_PERMISSION_NOT_SHOWN_NO_INPUT')
                time.sleep(.4)
        self.camera_permission_mode=True
        deny=camera_deny_control(self.snapshot('camera-permission-owned'))
        self.report['cameraDenialAttempted']=True;self.stage('DENY_OWNED_CAMERA_PERMISSION_ONCE');self.tap(deny)
        self.wait_focus_owner(False)
        state=self.run('shell','dumpsys','package',PACKAGE).decode('utf-8',errors='replace')
        if not re.search(r'android\.permission\.CAMERA:\s+granted=false\b',state):raise NativeFailure('CAMERA_DENIAL_NOT_CONFIRMED')
        self.report['cameraPermissionDenied']=True
        self.stage('OPEN_NEW_DECLARATION_AFTER_CAMERA_DENIAL')
        self.tap_text('Déclarer un incident','after-denial-new-incident')
        self.open_creation_context();self.tap(context_option(self.snapshot('after-denial-context')))
        self.stage('SELECT_TPE_CATEGORY_WITHOUT_FILE')
        for attempt in range(7):
            tree=self.snapshot('tpe-category-control');control=creation_category_control(tree)
            if exposed_point(control,self.width,self.height):break
            if attempt==6:raise NativeFailure('CATEGORY_NOT_EXPOSED_NO_TAP')
            up=context_scroll_up(tree,control);top,bottom=round(self.height*.27),round(self.height*.82)
            self.run('shell','input','swipe',str(self.width//2),str(top if up else bottom),str(self.width//2),str(bottom if up else top),'300')
        self.tap(control);self.tap(tpe_category_option(self.snapshot('tpe-category-picker')))
        description='Declaration TPE sans piece apres refus de la camera.'
        self.fill_incident_fields(fixture['newSubject'],description);self.confirm_incident_input(fixture['newSubject'],description)
        send=self.owned_button('Envoyer','after-denial-send')
        self.report['depositAttempted']=True;self.report['tpeCategorySelected']=True;self.stage('SEND_AFTER_DENIAL_WITHOUT_FILE_ONCE');self.tap(send)
        self.wait_text('Dépôt confirmé :','after-denial-deposit-confirmed')
        self.report['steps'].append('NATIVE_PHOTO_REFUSED_TPE_DECLARED_WITHOUT_FILE')

    def draft_create(self,fixture):
        self.login(fixture)
        self.stage('OPEN_DRAFT_FORM')
        self.tap_text('Ouvrir le menu','draft-menu',False)
        self.tap_text('Assistance · Incidents','draft-assistance')
        self.wait_text('Assistance · Incidents','draft-list')
        self.tap_text('Déclarer un incident','draft-new')
        self.open_creation_context();self.tap(context_option(self.snapshot('draft-context')))
        self.fill_incident_fields(fixture['subject'],fixture['description'])
        self.confirm_incident_input(fixture['subject'],fixture['description'])
        submit=self.owned_button('Enregistrer le brouillon','draft-save')
        self.report['draftCreationAttempted']=True;self.stage('SAVE_DRAFT_ONCE')
        self.tap(submit);self.wait_text('Brouillon enregistré.','draft-saved')
        self.report['steps'].append('NATIVE_DRAFT_CREATED')

    def select_draft_action(self,reference,subject,label):
        for attempt in range(7):
            tree=self.snapshot('draft-action-control');control=ticket_action_control(tree,reference,subject)
            if exposed_point(control,self.width,self.height):break
            if attempt==6:raise NativeFailure('DRAFT_ACTION_NOT_EXPOSED_NO_TAP')
            up=context_scroll_up(tree,control);top,bottom=round(self.height*.27),round(self.height*.82)
            self.run('shell','input','swipe',str(self.width//2),str(top if up else bottom),str(self.width//2),str(bottom if up else top),'300')
        self.tap(control);self.tap(draft_action_option(self.snapshot('draft-action-picker'),label))

    def draft_edit_submit(self,fixture):
        self.login(fixture)
        self.stage('REOPEN_SAVED_DRAFT')
        self.tap_text('Ouvrir le menu','resume-menu',False)
        self.tap_text('Assistance · Incidents','resume-assistance')
        self.wait_text('Assistance · Incidents','resume-list')
        self.tap(self.find(lambda node:node.get('class')=='android.widget.Button' and node.get('package')==PACKAGE and node.get('clickable')=='true' and
                          fixture['reference'] in node.get('text','') and fixture['draftSubject'] in node.get('text',''),'same-draft-card'))
        self.wait_text(fixture['reference']+' · Brouillon','same-draft-opened')
        self.select_draft_action(fixture['reference'],fixture['draftSubject'],'Modifier la description du brouillon')
        self.tap(self.owned_button('Modifier la description du brouillon','draft-edit-open'))
        self.wait_text('Modifier le brouillon','draft-editor')
        identity=(fixture['reference'],fixture['draftSubject'])
        for index,value in enumerate((fixture['subject'],fixture['description'])):
            for attempt in range(7):
                tree=self.snapshot('draft-edit-fields');field=draft_edit_fields(tree,*identity)[index]
                if exposed_point(field,self.width,self.height):break
                if attempt==6:raise NativeFailure('DRAFT_FIELD_NOT_EXPOSED_NO_INPUT')
                up=context_scroll_up(tree,field);top,bottom=round(self.height*.27),round(self.height*.82)
                self.run('shell','input','swipe',str(self.width//2),str(top if up else bottom),str(self.width//2),str(bottom if up else top),'300')
            self.stage('FILL_DRAFT_SUBJECT' if index==0 else 'FILL_DRAFT_DESCRIPTION')
            original=fixture['draftSubject'] if index==0 else fixture['draftDescription']
            original_matches=field.get('text')==original
            self.report.setdefault('draftOriginalInputCheck',[]).append(original_matches);self.save()
            if not original_matches:raise NativeFailure('SAVED_DRAFT_CONTENT_NOT_CONFIRMED_NO_EDIT')
            self.fill(field,value,incident_index=index,draft_identity=identity)
        for attempt in range(3):
            fields=draft_edit_fields(self.snapshot('draft-edit-check'),*identity)
            matches=[fields[0].get('text')==fixture['subject'],fields[1].get('text')==fixture['description']]
            self.report['draftEditInputCheck']=matches;self.save()
            if all(matches):break
            if attempt==2:raise NativeFailure('DRAFT_EDIT_INPUT_NOT_CONFIRMED_NO_SAVE')
            time.sleep(.5)
        save=self.owned_button('Enregistrer les modifications du brouillon','draft-edit-save')
        self.report['draftEditAttempted']=True;self.stage('SAVE_DRAFT_EDIT_ONCE');self.tap(save)
        self.wait_text('Brouillon modifié. Aucun avis envoyé.','draft-edit-saved')
        self.select_draft_action(fixture['reference'],fixture['subject'],'Envoyer le brouillon')
        submit=self.owned_button('Envoyer le brouillon','draft-submit')
        self.report['draftSubmissionAttempted']=True;self.stage('SUBMIT_SAME_DRAFT_ONCE');self.tap(submit)
        self.wait_text('Action enregistrée.','draft-submit-confirmed')
        self.wait_text(fixture['reference']+' · Ouvert','same-draft-now-open')
        self.report['reference']=fixture['reference'];self.report['steps'].append('NATIVE_DRAFT_SENT')

    def open_creation_context(self):
        for attempt in range(7):
            tree=self.snapshot('context-choice');control=creation_context_control(tree)
            if exposed_point(control,self.width,self.height):
                self.tap(control);return
            if attempt<6:
                up=context_scroll_up(tree,control);top,bottom=round(self.height*.27),round(self.height*.82)
                self.run('shell','input','swipe',str(self.width//2),str(top if up else bottom),str(self.width//2),str(bottom if up else top),'300')
        raise NativeFailure('CONTEXT_NOT_EXPOSED_NO_TAP')

    def confirm_incident_input(self,subject,description):
        for attempt in range(3):
            fields=[n for n in self.snapshot('incident-input-check').iter('node') if n.get('class')=='android.widget.EditText']
            structure=len(fields)==2 and all(n.get('package')==PACKAGE and n.get('password')=='false' for n in fields)
            summary={'subjectMatches':structure and fields[0].get('text')==subject,'descriptionMatches':structure and fields[1].get('text')==description}
            self.report['incidentInputCheck']=summary;self.save()
            if all(summary.values()):return
            if attempt<2:time.sleep(.5)
        raise NativeFailure('INCIDENT_INPUT_NOT_CONFIRMED_NO_SEND')

    def fill_incident_fields(self,subject,description):
        for index, value in enumerate((subject,description)):
            self.stage('FILL_SUBJECT' if index == 0 else 'FILL_DESCRIPTION')
            # Index only within this known new-incident form, never the login page.
            found = None
            for attempt in range(7):
                tree = self.snapshot('new-incident-fields')
                fields = [node for node in tree.iter('node') if node.get('class') == 'android.widget.EditText']
                if len(fields) != 2:
                    raise NativeFailure('INCIDENT_FIELDS_AMBIGUOUS_NO_INPUT')
                found = fields[index]
                if exposed_point(found, self.width, self.height):
                    break
                self.run('shell', 'input', 'swipe', str(self.width // 2), str(round(self.height * .82)), str(self.width // 2), str(round(self.height * .27)), '300')
            self.fill(found, value,incident_index=index)

    def form_input_probe(self):
        expected=('Diagnostic subject 0123','Diagnostic description 4567 with spaces.')
        self.fill_incident_fields(*expected)
        fields=[n for n in self.snapshot('fixed-form-input').iter('node') if n.get('class')=='android.widget.EditText']
        if len(fields)!=2:raise NativeFailure('INCIDENT_FIELDS_AMBIGUOUS_NO_INPUT')
        self.report['fixedFormProbe']=[{'matches':n.get('text')==v,'length':len(n.get('text','')),'spaces':n.get('text','').count(' '),'startsCorrectly':n.get('text','').startswith(v[:4]),'endsCorrectly':n.get('text','').endswith(v[-4:])} for n,v in zip(fields,expected)]
        self.save()
        if not all(row['matches'] for row in self.report['fixedFormProbe']):raise NativeFailure('FIXED_FORM_INPUT_NOT_CONFIRMED_NO_SEND')
        self.report['steps'].append('NATIVE_FORM_INPUT_PROBE_OK_NO_DEPOSIT')

    def close(self):
        if self.created_remote:
            self.run('shell', 'rm', '-f', self.remote + '/hierarchy.xml', timeout=10)
            self.run('shell', 'rmdir', self.remote, timeout=10)
            self.created_remote = False


def main():
    parser = argparse.ArgumentParser()
    for key in ('adb', 'serial', 'avd', 'apk-sha', 'output', 'phase'):
        parser.add_argument('--' + key, required=True)
    parser.add_argument('--fixture')
    args = parser.parse_args()
    ui, step = None, 'PREVOL'
    try:
        if args.phase not in ('inspect', 'declare', 'replay-deposit', 'required-other', 'conversation', 'commercial-conversation', 'context-switch', 'photo-capture', 'revocation', 'resolution', 'draft-create', 'draft-edit-submit', 'attachments', 'photo-refuse-declare', 'diagnose', 'system-diagnose', 'input-probe', 'form-input-probe', 'context-probe'):
            raise NativeFailure('PHASE_INVALID')
        fixture = {}
        if args.phase == 'input-probe':
            # Fixed diagnostic strings, no account, no authentication request.
            fixture = {'login':'DiagnosticUser123','password':'DiagnosticPass0123456789_ABCDEFGHIJKLMNOP-xyz'}
        if args.phase in ('declare','replay-deposit','required-other','conversation','commercial-conversation','context-switch','photo-capture','revocation','resolution','draft-create','draft-edit-submit','attachments','photo-refuse-declare'):
            path = Path(args.fixture or '')
            if path.is_symlink() or path.parent.is_symlink() or getattr(path.parent, 'is_junction', lambda: False)() or not path.is_file() or path.stat().st_size > 16000 or not re.fullmatch(r'native-fixture-[a-f0-9]{32}\.private', path.name) or path.parent.name != 'private':
                raise NativeFailure('PRIVATE_FIXTURE_INVALID')
            fixture = json.loads(path.read_text(encoding='utf-8-sig'))
            for key in ('login', 'password', 'subject'):
                private_input_command(fixture.get(key))
            if 'contextLabel' in fixture and not re.fullmatch(r'INC-HTTP-[a-f0-9]{8}',fixture['contextLabel']):
                raise NativeFailure('SYNTHETIC_DOSSIER_LABEL_INVALID')
            if args.phase=='conversation':
                conversation_service_texts(fixture)
                for key in ('reference','supportReply','reply','internalNote'):private_input_command(fixture.get(key))
                if not re.fullmatch(r'INC-[a-f0-9-]{36}',fixture['reference']):raise NativeFailure('CONVERSATION_REFERENCE_INVALID')
            if args.phase=='commercial-conversation':commercial_conversation_fixture(fixture)
            if args.phase=='context-switch':context_switch_fixture(fixture)
            if args.phase=='photo-capture':
                if not re.fullmatch(r'INC-[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}',fixture.get('reference','')):raise NativeFailure('PHOTO_REFERENCE_INVALID')
            if args.phase=='revocation':
                for key in ('reference','supportReply'):private_input_command(fixture.get(key))
                if not re.fullmatch(r'INC-[a-f0-9-]{36}',fixture['reference']):raise NativeFailure('REVOCATION_REFERENCE_INVALID')
            if args.phase=='resolution':
                for key in ('resolutionText','reopenText'):private_input_command(fixture.get(key))
                if not re.fullmatch(r'INC-[a-f0-9-]{36}',fixture.get('reference','')):raise NativeFailure('RESOLUTION_REFERENCE_INVALID')
            if args.phase in ('draft-create','draft-edit-submit'):private_input_command(fixture.get('description'))
            if args.phase=='draft-edit-submit':
                private_input_command(fixture.get('draftSubject'))
                private_input_command(fixture.get('draftDescription'))
                if not re.fullmatch(r'INC-[a-f0-9-]{36}',fixture.get('reference','')):raise NativeFailure('DRAFT_REFERENCE_INVALID')
            if args.phase=='attachments':
                if (not re.fullmatch(r'INC-[a-f0-9-]{36}',fixture.get('reference','')) or
                        not isinstance(fixture.get('files'),list) or len(fixture['files'])!=3 or
                        {item.get('contentType') for item in fixture['files']} != set(FILE_EXTENSIONS)):
                    raise NativeFailure('ATTACHMENT_FIXTURE_INVALID')
                for item in fixture['files']: attachment_export_name(item)
            if args.phase=='photo-refuse-declare':
                private_input_command(fixture.get('newSubject'))
                if not re.fullmatch(r'INC-[a-f0-9-]{36}',fixture.get('reference','')):raise NativeFailure('PHOTO_REFERENCE_INVALID')
        ui = NativeUi(args.adb, args.serial, args.avd, args.apk_sha, args.output, (fixture.get('login'), fixture.get('password')))
        ui.allow_resource_read_retry=args.phase in ('context-switch','photo-capture')
        ui.system_recovery=args.phase=='system-diagnose'
        ui.save()
        ui.validate_device(args.phase in ('diagnose','system-diagnose'))
        step = 'NATIVE_' + args.phase.upper()
        if args.phase in ('diagnose','system-diagnose'):
            ui.snapshot('system-diagnostic', allow_system_dialog=True)
            ui.report['result'] = 'DIAGNOSTIC_ONLY'
            ui.save()
            print('NATIVE_DIAGNOSTIC_ONLY|NO_INPUT_OR_TAP')
            return 0
        if args.phase == 'context-probe':
            ui.open_creation_context();context_option(ui.snapshot('context-probe'))
            ui.report['steps'].append('NATIVE_CONTEXT_PROBE_OK_NO_DEPOSIT')
        elif args.phase == 'form-input-probe':
            ui.form_input_probe()
        elif args.phase == 'input-probe':
            try:
                ui.prepare_login_input(fixture)
            except NativeFailure as problem:
                if str(problem)=='LOGIN_INPUT_NOT_CONFIRMED_NO_SEND':
                    # Numeric diagnostics apply only to these fixed non-account strings.
                    tree=ui.snapshot('fixed-diagnostic-input')
                    ui.report['fixedProbeInputLengths']=[len(n.get('text','')) for n in tree.iter('node') if n.get('class')=='android.widget.EditText']
                    ui.save()
                raise
            ui.report['steps'].append('NATIVE_INPUT_PROBE_OK_NO_AUTHENTICATION')
        elif args.phase == 'declare':
            ui.declare(fixture)
        elif args.phase == 'replay-deposit':
            ui.replay_deposit(fixture)
        elif args.phase == 'required-other':
            ui.required_other(fixture)
        elif args.phase == 'conversation':
            ui.conversation(fixture)
        elif args.phase == 'context-switch':
            ui.context_switch(fixture)
        elif args.phase == 'photo-capture':
            import importlib.util
            module_path=Path(__file__).with_name('incident_native_camera.py')
            if module_path.is_symlink() or not module_path.is_file():raise NativeFailure('PHOTO_PILOT_REQUIRED')
            spec=importlib.util.spec_from_file_location('incident_native_camera',module_path)
            module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
            module.capture_photo(ui,fixture)
        elif args.phase == 'commercial-conversation':
            ui.conversation(fixture,public_key='publicReply')
        elif args.phase == 'revocation':
            ui.revocation(fixture)
        elif args.phase=='resolution':
            ui.resolution(fixture)
        elif args.phase=='draft-create':
            ui.draft_create(fixture)
        elif args.phase=='draft-edit-submit':
            ui.draft_edit_submit(fixture)
        elif args.phase=='attachments':
            ui.attachments(fixture)
        elif args.phase=='photo-refuse-declare':
            ui.photo_refuse_declare(fixture)
        else:
            ui.snapshot('inspect')
            ui.report['steps'].append('NATIVE_INSPECT_OK')
        ui.report['result'] = 'PASS'
        ui.save()
        if args.phase=='photo-capture':
            print('NATIVE_PHOTO_CAPTURE_AND_QUARANTINE_OK')
            return 0
        print('NATIVE_CONTEXT_SWITCH_AND_PDV_FILTER_OK' if args.phase=='context-switch' else 'NATIVE_COMMERCIAL_CONVERSATION_OK' if args.phase=='commercial-conversation' else 'NATIVE_LOST_ACK_MANUAL_EXACT_RETRY_OK' if args.phase=='replay-deposit' else 'NATIVE_LIVE_REVOCATION_LOGOUT_RELOGIN_OK' if args.phase=='revocation' else 'NATIVE_RESOLUTION_CONFIRMED_AND_REOPENED' if args.phase=='resolution' else 'NATIVE_REQUIRED_REJECTED_OTHER_DECLARED' if args.phase=='required-other' else 'NATIVE_PHOTO_REFUSED_TPE_DECLARED_WITHOUT_FILE' if args.phase=='photo-refuse-declare' else 'NATIVE_ATTACHMENTS_SAVED_AND_CANCELLED' if args.phase=='attachments' else 'NATIVE_DRAFT_CREATED' if args.phase=='draft-create' else 'NATIVE_DRAFT_SENT' if args.phase=='draft-edit-submit' else 'NATIVE_CONVERSATION_OK' if args.phase=='conversation' else 'NATIVE_CONTEXT_PROBE_OK_NO_DEPOSIT' if args.phase == 'context-probe' else 'NATIVE_FORM_INPUT_PROBE_OK_NO_DEPOSIT' if args.phase == 'form-input-probe' else 'NATIVE_INPUT_PROBE_OK_NO_AUTHENTICATION' if args.phase == 'input-probe' else 'NATIVE_DECLARATION_OK' if args.phase == 'declare' else 'NATIVE_INSPECT_OK')
        return 0
    except Exception as problem:
        code = str(problem) if isinstance(problem, NativeFailure) else 'UNCLASSIFIED_SAFE_FAILURE'
        if ui:
            ui.report.update(result='FAIL', failedStep=step, failureCode=code)
            ui.save()
        print('NATIVE_CASE_FAILED|' + step + '|' + code)
        return 1
    finally:
        if ui:
            try:
                ui.close()
                ui.report['cleanupVerified'] = True
                ui.save()
            except Exception:
                ui.report['cleanupIncomplete'] = True
                ui.save()


if __name__ == '__main__':
    raise SystemExit(main())

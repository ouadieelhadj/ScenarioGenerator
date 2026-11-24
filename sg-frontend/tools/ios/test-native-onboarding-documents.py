"""Contract tests: minimal public DTOs and no business API writes during preparation."""
from pathlib import Path
import copy, hashlib, importlib.util, io, json, tempfile, unittest
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('kyc',Path(__file__).with_name('prepare-native-onboarding-documents.py'))
kyc=importlib.util.module_from_spec(spec);spec.loader.exec_module(kyc)

class Api:
    ORIGIN='https://merchant.example.test'
    def __init__(self):
        self.calls=[]
        self.fixture={'id':'synthetic-id','reference':'ONB-00000001','login':'fp-u999999','legalName':'RECETTE IPHONE SYNTHETIQUE','outletCode':'TEST-OUTLET'}
        # V2 intentionally has neither acquirerId nor kycStatus: these belong to V1.
        self.full={'id':'synthetic-id','reference':'ONB-00000001','legalName':self.fixture['legalName'],'merchantType':'PM','status':'DRAFT','version':1,'outlets':[{'code':'TEST-OUTLET'}]}
        self.own={'id':'synthetic-id','acquirerId':'DAMANCASH','kycStatus':'NOT_STARTED'}
        self.docs=[];self.checklist={'selectionRequired':False,'merchantType':'PM','documents':[{'type':'BANK_ACCOUNT_PROOF','label':'Pièce fictive'}]}
    def load_fixture(self):return copy.deepcopy(self.fixture)
    def authenticated(self,fixture,password):return 'opaque-test-session'
    def request(self,path,token=None,body=None):
        self.calls.append(('GET' if body is None else 'POST',path))
        if path.endswith('/auth/login'):return {'token':'opaque-test-session'}
        if path.endswith('/verified-mine'):return self.own
        if path.endswith('/v2/dossiers/synthetic-id'):return self.full
        if path.endswith('/commercial/workspace'):return {'actions':['EDIT']}
        if path.endswith('/documents'):return self.docs
        if path.endswith('/kyc-requirements'):return self.checklist
        if path.endswith('/attachments'):return [{'id':kyc.SOURCE_ATTACHMENT,'scanState':'CLEAN','contentType':'image/png','createdAt':'2026-11-22T12:00:00Z'}]
        raise AssertionError('Unexpected business endpoint '+path)

class Contracts(unittest.TestCase):
    def setUp(self):
        self.api=Api();self.content=b'SYNTHETIC-ONLY'.ljust(1000000,b'0');self.digest=hashlib.sha256(self.content).hexdigest()
        self.tmp=tempfile.TemporaryDirectory();self.output=Path(self.tmp.name)/'report.json'
        self.p1=patch.object(kyc,'api',return_value=self.api);self.p1.start()
        self.p2=patch.object(kyc,'SYNTHETIC_HASH',self.digest);self.p2.start()
        self.p3=patch.object(kyc.urllib.request,'urlopen',side_effect=lambda *a,**kw:io.BytesIO(self.content));self.p3.start()
    def tearDown(self):self.p3.stop();self.p2.stop();self.p1.stop();self.tmp.cleanup()
    def prepare(self):return kyc.prepare(self.output,'synthetic-not-a-real-password')
    def test_minimal_v1_v2_contract_and_no_business_write(self):
        context=self.prepare();self.assertEqual(context['versionBefore'],1)
        report=json.loads(self.output.read_text());self.assertFalse(report['businessWritesByHelper'])
        self.assertTrue(report['nativeFilesSaveRequiredBeforeSelection'])
        self.assertEqual([path for method,path in self.api.calls if method=='POST'],['/api-merchant-portal/auth/login'])
    def test_foreign_owner_stops_before_native_preparation(self):
        self.api.own['id']='foreign-id'
        with self.assertRaises(AssertionError):self.prepare()
        self.assertFalse(self.output.exists())
    def test_non_saved_dossier_stops(self):
        self.api.full['version']=0
        with self.assertRaises(AssertionError):self.prepare()
    def test_existing_document_stops_duplicate_campaign(self):
        self.api.docs=[{'id':'already-uploaded'}]
        with self.assertRaises(AssertionError):self.prepare()
    def test_missing_editor_right_stops(self):
        original=self.api.request
        self.api.request=lambda path,*a,**kw: {'actions':[]} if path.endswith('/commercial/workspace') else original(path,*a,**kw)
        with self.assertRaises(AssertionError):self.prepare()
    def test_wrong_source_bytes_stop_before_uploads(self):
        with patch.object(kyc,'SYNTHETIC_HASH','0'*64):
            with self.assertRaises(AssertionError):self.prepare()
    def test_source_without_metadata_size_uses_download_size(self):
        # Real Incident.Attachment has no sizeBytes attribute. A short download
        # must still fail; removing a nonexistent metadata field weakens no gate.
        with patch.object(kyc.urllib.request,'urlopen',return_value=io.BytesIO(self.content[:-1])):
            with self.assertRaises(AssertionError):self.prepare()
    def test_source_download_identifies_the_authorized_checker(self):
        seen=[]
        def download(request,**kwargs):
            seen.append(request.get_header('User-agent'))
            return io.BytesIO(self.content)
        with patch.object(kyc.urllib.request,'urlopen',side_effect=download):self.prepare()
        self.assertEqual(seen,['FuturPayment-Recipe/1.0 (authorized demo health check)'])
    def test_unclean_source_stops_before_download(self):
        original=self.api.request
        self.api.request=lambda path,*a,**kw: [{'id':kyc.SOURCE_ATTACHMENT,'scanState':'QUARANTINED','contentType':'image/png'}] if path.endswith('/attachments') else original(path,*a,**kw)
        with self.assertRaises(AssertionError):self.prepare()
    def test_wrong_source_mime_stops(self):
        original=self.api.request
        self.api.request=lambda path,*a,**kw: [{'id':kyc.SOURCE_ATTACHMENT,'scanState':'CLEAN','contentType':'application/pdf'}] if path.endswith('/attachments') else original(path,*a,**kw)
        with self.assertRaises(AssertionError):self.prepare()
    def test_readonly_reconciliation_exact_and_changed_bytes(self):
        context=self.prepare();self.api.full['version']=2
        self.api.docs=[{'id':'synthetic-doc','caseId':'synthetic-id','type':'BANK_ACCOUNT_PROOF','version':1,'contentType':'image/png','contentLength':1000000,'sha256':self.digest,'reviewStatus':'PENDING','reviewedBy':None,'uploadedBy':'synthetic-user'}]
        result=kyc.verify(context,'synthetic-not-a-real-password',self.output)
        self.assertTrue(result['allExecutedChecksPassed']);self.assertFalse(result['businessWritesByHelper'])
        self.api.docs[0]['sha256']='0'*64
        result=kyc.verify(context,'synthetic-not-a-real-password',self.output)
        self.assertFalse(result['allExecutedChecksPassed'])


    def existing(self, kind='BANK_ACCOUNT_PROOF', **overrides):
        document={'id':'preserved-native-document','caseId':'synthetic-id','type':kind,'version':1,
                  'reviewStatus':'PENDING','reviewedBy':None,'contentType':'image/png','contentLength':1000000,
                  'sha256':self.digest,'uploadedBy':self.api.fixture['login']}
        document.update(overrides);return document

    def test_identical_preserved_document_uploads_only_missing_type(self):
        self.api.docs=[self.existing()];self.api.full['version']=2
        self.api.checklist['documents'].append({'type':'STATUTES','label':'Statuts fictifs'})
        context=self.prepare()
        self.assertEqual(context['versionBefore'],2)
        self.assertEqual([d['type'] for d in context['documents']],['STATUTES'])
        self.assertEqual(len(context['allDocuments']),2)
        self.assertEqual(context['existingDocuments'][0]['id'],'preserved-native-document')
        self.assertEqual([path for method,path in self.api.calls if method=='POST'],['/api-merchant-portal/auth/login'])

    def test_complete_existing_set_never_reuploads(self):
        self.api.docs=[self.existing()];self.api.full['version']=2
        with self.assertRaises(AssertionError):self.prepare()
        self.assertFalse(self.output.exists())

    def test_foreign_existing_author_blocks_recovery(self):
        self.api.docs=[self.existing(uploadedBy='another-account')];self.api.full['version']=2
        with self.assertRaises(AssertionError):self.prepare()

    def test_changed_existing_bytes_block_recovery(self):
        self.api.docs=[self.existing(sha256='0'*64)];self.api.full['version']=2
        with self.assertRaises(AssertionError):self.prepare()

    def test_unrelated_dossier_revision_blocks_recovery(self):
        self.api.docs=[self.existing()];self.api.full['version']=3
        with self.assertRaises(AssertionError):self.prepare()

    def test_duplicate_existing_type_blocks_recovery(self):
        self.api.docs=[self.existing(),self.existing(id='another-version')];self.api.full['version']=3
        with self.assertRaises(AssertionError):self.prepare()


    def test_read_only_complete_set_has_no_uploads_and_no_mutation(self):
        self.api.docs=[self.existing()];self.api.full['version']=2
        context=kyc.prepare(self.output,'synthetic-not-a-real-password',read_only_complete=True)
        self.assertEqual(context['documents'],[])
        self.assertEqual(len(context['allDocuments']),1)
        report=json.loads(self.output.read_text());self.assertTrue(report['readOnlyCompleteSet'])
        self.assertFalse(report['nativeFilesSaveRequiredBeforeSelection'])
        self.assertEqual([path for method,path in self.api.calls if method=='POST'],['/api-merchant-portal/auth/login'])

    def test_read_only_mode_refuses_incomplete_set(self):
        with self.assertRaises(AssertionError):kyc.prepare(self.output,'synthetic-not-a-real-password',read_only_complete=True)
        self.assertFalse(self.output.exists())

if __name__=='__main__':unittest.main()

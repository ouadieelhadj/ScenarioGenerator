import type { PersonalAssociation } from '../../app/core/services/personal-merchant-association.service';

/** Display the server-authorized associations, never infer merchant operating status. */
export function mobileAuthorizedCommerces(rows: readonly PersonalAssociation[]): PersonalAssociation[] {
  const seen = new Set<string>();
  return rows.filter(row => {
    if (row.state !== 'ACTIVE' || !row.hierarchyNodeId || !row.acquirerCode || !row.hierarchyLabel?.trim()) return false;
    const key = JSON.stringify([row.acquirerCode, row.hierarchyNodeId]);
    if (seen.has(key)) return false;
    seen.add(key); return true;
  });
}

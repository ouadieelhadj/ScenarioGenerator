/** An obsolete response must not close a newly opened session. */
export function shouldEndMobileSession(status: number, trusted: boolean, sentToken: string | null, currentToken: string | null): boolean {
  return status === 401 && trusted && !!sentToken && sentToken === currentToken;
}

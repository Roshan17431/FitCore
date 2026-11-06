// Test-only classification of observed native restart proof; metadata grants no authority.
export function restartProof({status,metadata,marker,cookiePresence}) {
  const invalid=()=>{throw Error('Unexpected or unbound native restart cookie/status evidence');};
  if(!cookiePresence || typeof cookiePresence.auth!=='boolean' || typeof cookiePresence.preauth!=='boolean')return invalid();
  if(status===401 && metadata?.message==='BROWSER_CONTEXT_REQUIRED' && !cookiePresence.auth && !cookiePresence.preauth)return false;
  if(status!==200 || !cookiePresence.auth || marker?.kind!=='LOGIN' ||
     !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(marker.preFamilyId||'') ||
     !/^(0|[1-9][0-9]{0,18})$/.test(marker.preServerRevision||''))return invalid();
  const next=BigInt(marker.preServerRevision)+1n;
  if(next>9223372036854775807n || metadata?.familyId!==marker.preFamilyId ||
     metadata.contextRevision!==String(next) || metadata.familyRevision!==String(next) ||
     metadata.currentContext!==true || metadata.contextKind!=='AUTH' || metadata.transitionState!=='PENDING' ||
     metadata.pendingKind!=='LOGIN' || metadata.authenticated!==false || metadata.reauthenticationRequired!==false ||
     typeof metadata.csrfToken!=='string' || !metadata.csrfToken || metadata.csrfToken.length>4096)return invalid();
  return true;
}

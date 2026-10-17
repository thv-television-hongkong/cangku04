package contract

import (
 "encoding/json"
 "testing"
 "github.com/stretchr/testify/require"
)

func TestAppendOnlyEventsAndRefund(t *testing.T) {
 stub:=newMockStub(t)
 invoke(t,stub,"init","InitLedger")
 first:=invoke(t,stub,"first","AppendEvidence","PROJECT:1:ONLINE:1",BizProject,"1","1",hashProject,"","ONLINE","ADMIN")
 var a ChainResult; require.NoError(t,json.Unmarshal(first,&a))
 invoke(t,stub,"change","AppendEvidence","PROJECT:1:OFFLINE:2",BizProject,"1","1",hashUsage,"","OFFLINE","ADMIN")
 var original Evidence
 require.NoError(t,json.Unmarshal(invoke(t,stub,"query","QueryByTxId","first"),&original))
 require.Equal(t,hashProject,original.DataHash)
 require.Equal(t,"PROJECT:1:ONLINE:1",original.EventKey)
 var duplicate ChainResult
 require.NoError(t,json.Unmarshal(invoke(t,stub,"repeat","AppendEvidence","PROJECT:1:ONLINE:1",BizProject,"1","1",hashProject,"","ONLINE","ADMIN"),&duplicate))
 require.Equal(t,a.TxId,duplicate.TxId)
 conflict:=stub.MockInvoke("conflict",[][]byte{[]byte("AppendEvidence"),[]byte("PROJECT:1:ONLINE:1"),[]byte(BizProject),[]byte("1"),[]byte("1"),[]byte(hashUsage),[]byte(""),[]byte("ONLINE"),[]byte("ADMIN")})
 require.NotEqual(t,int32(200),conflict.Status)
 invoke(t,stub,"refund","CreateRefundEvidence","REFUND:9:REFUNDED:1","9","1",hashDonation,hashFile,"REFUNDED","ADMIN")
 var refund Evidence
 require.NoError(t,json.Unmarshal(invoke(t,stub,"get-refund","QueryByEvent","REFUND:9:REFUNDED:1"),&refund))
 require.Equal(t,BizRefund,refund.BizType)
 require.Equal(t,hashFile,refund.FileHash)
 require.Equal(t,"true",string(invoke(t,stub,"integrity","VerifyChainIntegrity")))
}

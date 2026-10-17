<template>
  <div class="app-container">
    <div class="workspace-heading"><div><h2>{{ finance ? '学校财务 · 捐赠确认' : '我的模拟订单' }}</h2><p>每一份善意都有记录。支付凭证经学校核对后，才计入项目筹款。</p></div><el-tag type="warning">模拟支付 · 无实际扣款</el-tag></div>
    <el-card shadow="never">
      <el-form inline><el-form-item label="订单状态"><el-select v-model="query.status" clearable style="width:200px"><el-option v-for="s in states" :key="s" :value="s" :label="label(s)" /></el-select></el-form-item><el-form-item><el-button type="primary" @click="query.current=1;load()">查询</el-button><el-button @click="load">刷新</el-button></el-form-item></el-form>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="orderNo" label="订单编号" min-width="240" show-overflow-tooltip />
        <el-table-column prop="projectTitle" label="公益项目" min-width="160" />
        <el-table-column label="金额" width="120"><template #default="{row}">￥{{ amount(row.amount) }}</template></el-table-column>
        <el-table-column label="状态" width="160"><template #default="{row}"><el-tag :type="(row.displayStatus||row.status)==='CONFIRMED'?'success':'info'">{{ label(row.displayStatus||row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="捐赠存证" min-width="180"><template #default="{row}">{{ evidence(row.evidenceStatus) }}</template></el-table-column>
        <el-table-column label="支付时间" min-width="170"><template #default="{row}">{{ time(row.paidTime) }}</template></el-table-column>
        <el-table-column label="操作" width="230" fixed="right"><template #default="{row}">
          <el-button link type="primary" @click="show(row)">详情</el-button>
          <el-button v-if="!finance && ['CREATED','PAY_FAILED','FINANCE_REJECTED'].includes(row.status)" link type="primary" @click="open(row,'pay')">{{ row.status==='FINANCE_REJECTED'?'重新提交审核':'模拟支付' }}</el-button>
          <el-button v-if="canConfirm && row.status==='WAIT_CONFIRM'" link type="primary" @click="open(row,'confirm')">财务核对</el-button>
        </template></el-table-column>
      </el-table>
      <div class="table-pagination"><el-pagination layout="total,prev,pager,next" :total="total" v-model:current-page="query.current" :page-size="query.size" @current-change="load" /></div>
    </el-card>
    <el-dialog v-model="visible" :title="mode==='confirm'?'核对模拟支付订单':mode==='detail'?'订单与财务审核记录':'模拟支付'" width="min(680px,94vw)" :close-on-click-modal="!busy" :close-on-press-escape="!busy" :show-close="!busy">
      <el-descriptions :column="1" border><el-descriptions-item label="公益项目">{{ current.projectTitle }}</el-descriptions-item><el-descriptions-item label="订单">{{ current.orderNo }}</el-descriptions-item><el-descriptions-item label="金额">￥{{ amount(current.amount) }}</el-descriptions-item><el-descriptions-item label="业务状态">{{ label(current.displayStatus||current.status) }}</el-descriptions-item><el-descriptions-item label="捐赠存证状态">{{ evidence(current.evidenceStatus) }}</el-descriptions-item><el-descriptions-item v-if="current.evidenceTxId" label="存证交易 ID">{{ current.evidenceTxId }}</el-descriptions-item></el-descriptions>
      <el-alert :title="next(current.displayStatus||current.status)" type="info" :closable="false" style="margin-top:16px" /><el-alert v-if="lastRejection" :title="'财务驳回原因：'+lastRejection" type="warning" :closable="false" style="margin-top:12px" />
      <p v-if="current.voucherFileId"><el-button link type="primary" @click="download(current.voucherFileId)">下载当前支付凭证并核对</el-button></p>
      <template v-if="mode==='detail'">
        <el-timeline style="margin-top:24px"><el-timeline-item v-for="c in confirmations" :key="c.confirmationId" :timestamp="time(c.confirmTime)">{{ c.result==='PASS'?'财务已确认':'财务驳回' }} · 核对金额 ￥{{ amount(c.checkedAmount) }}<p>{{ c.opinion || '未填写补充意见' }}</p></el-timeline-item></el-timeline>
        <el-empty v-if="!confirmations.length" description="尚无财务审核记录" />
        <el-button v-if="current.donationId && !finance" type="primary" @click="$router.push('/donor/donations')">查看有效捐赠与存证</el-button>
      </template>
      <el-form v-else :disabled="busy" :model="form" label-width="120px" style="margin-top:20px">

        <el-form-item v-if="mode==='confirm'" label="核对金额"><el-input-number v-model="form.checkedAmount" :precision="2" :min="0.01" /></el-form-item>
        <el-form-item v-if="mode==='confirm'" label="审核结果"><el-radio-group v-model="form.result"><el-radio value="PASS">确认入账</el-radio><el-radio value="REJECT">驳回</el-radio></el-radio-group></el-form-item>
        <el-form-item :label="mode==='confirm'?'审核意见':'补充说明'"><el-input v-model="form.opinion" type="textarea" :maxlength="mode==='confirm'?1000:500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button :disabled="busy" @click="visible=false">关闭</el-button><template v-if="mode!=='detail'"><el-button v-if="mode==='pay' && current.status!=='FINANCE_REJECTED'" :disabled="busy" @click="submit('FAILED')">模拟支付失败</el-button><el-button type="primary" :loading="busy" @click="submit('SUCCESS')">{{ mode==='confirm'?'提交财务审核':current.status==='FINANCE_REJECTED'?'重新提交审核':'模拟支付成功' }}</el-button></template></template>
    </el-dialog>
  </div>
</template>
<script>
import { listOrders, orderDetail, mockPay, resubmitOrder, confirmOrder, requestKey, statusLabel, evidenceLabel, orderNextStep } from '@/api/finance'
import { downloadFile } from '@/api/file'
import refreshWorkspace from '@/utils/refreshWorkspace'
import { formatAmount, formatTime } from '@/utils/format'
export default {
  name:'OrderWorkspace',mixins:[refreshWorkspace],
  data:()=>({list:[],total:0,loading:false,busy:false,visible:false,mode:'detail',current:{},confirmations:[],form:{},query:{current:1,size:10,status:''},states:['CREATED','PAY_FAILED','WAIT_CONFIRM','FINANCE_REJECTED','CONFIRMED','REFUNDING','REFUNDED']}),
  computed:{finance(){return ['ADMIN','AUDITOR'].includes(this.$store.getters.role)},canConfirm(){return this.$store.getters.role==='ADMIN'},lastRejection(){const rejected=this.confirmations.filter(c=>c.result==='REJECT');return rejected.length?rejected[rejected.length-1].opinion:''}},
  created(){this.load()},
  methods:{label:statusLabel,evidence:evidenceLabel,next:orderNextStep,amount:formatAmount,time:formatTime,
    async load(){this.loading=true;try{const r=await listOrders(this.query,this.finance);this.list=r.data.records;this.total=r.data.total}finally{this.loading=false}},
    async show(row){const r=await orderDetail(row.orderId);this.current=r.data.order;this.confirmations=r.data.confirmations;this.mode='detail';this.visible=true},
    async open(row,mode){if(this.busy)return;const r=await orderDetail(row.orderId);row=r.data.order;this.confirmations=r.data.confirmations;this.current=row;this.mode=mode;this.form={orderId:row.orderId,result:'PASS',checkedAmount:Number(row.amount),voucherFileId:mode==='confirm'?row.voucherFileId:null,opinion:'',requestKey:requestKey()};this.visible=true},
    async download(id){try{await downloadFile(id)}catch(e){this.$message.error(e.message)}},
    async submit(result){if(this.busy)return;if(this.mode==='confirm'&&this.form.result==='REJECT'&&!this.form.opinion.trim())return this.$message.warning('请填写驳回意见');this.busy=true;try{if(this.mode==='confirm')await confirmOrder(this.form);else if(this.current.status==='FINANCE_REJECTED')await resubmitOrder(this.current.orderId,{remark:this.form.opinion});else await mockPay(this.current.orderId,{result,remark:this.form.opinion});this.$message.success(this.mode==='confirm'?(this.form.result==='PASS'?'财务确认成功，有效捐赠已入账，存证后台处理中':'财务已驳回，学生可补充说明重新提交'):result==='FAILED'?'已记录支付失败，可重试':'模拟支付已登记，等待学校财务核对');this.visible=false;await this.load()}finally{this.busy=false}}
  }
}
</script>

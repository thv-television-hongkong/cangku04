<template>
  <div class="app-container identity-workspace">
    <div class="workspace-heading"><div><h2>{{ admin ? '学校校园身份审核' : '校园身份认证' }}</h2><p>确认校园信息，开始记录每一份善意。</p></div><el-button :loading="loading" @click="load">刷新</el-button></div>
    <el-alert v-if="config.testMode" title="内部测试认证，不代表学校正式实名认证" description="当前为内部体验环境。学生填写并确认校园信息即可完成测试认证，无需上传身份证明、无需等待学校人工审核。原有申请与材料继续保留。" type="warning" show-icon :closable="false" style="margin-bottom:20px" />
    <el-card v-if="!admin" shadow="never" style="margin-bottom:20px">
      <div class="identity-current"><el-tag :type="profile.authStatus===2?'success':'info'">{{ identityLabel }}</el-tag><el-button v-if="profile.authStatus===2" type="primary" @click="$router.push('/donor/projects')">浏览公益项目</el-button></div>
      <el-form label-width="110px" style="max-width:600px" :disabled="busy || !configLoaded">
        <el-form-item label="学号" required><el-input v-model="form.identityNo" maxlength="64" placeholder="填写本人的校园学号" /></el-form-item>
        <template v-if="config.testMode">
          <el-form-item label="姓名" required><el-input v-model="form.realName" maxlength="64" placeholder="填写校园身份姓名" /></el-form-item>
          <el-form-item label="学校 / 院系" required><el-input v-model="form.college" maxlength="64" placeholder="例如：计算机学院" /></el-form-item>
          <el-form-item><el-checkbox v-model="form.acknowledged">我确认以上信息仅用于内部测试认证</el-checkbox></el-form-item>
        </template>
        <el-form-item v-else label="校园身份证明" required><el-upload v-model:file-list="fileList" action="#" :auto-upload="false" :limit="1" :on-change="upload" :on-remove="()=>form.fileId=null" :disabled="uploading || busy"><el-button :loading="uploading">上传学生证或校园证明</el-button></el-upload></el-form-item>
        <el-form-item><el-button type="primary" :loading="busy" :disabled="uploading || !configLoaded" @click="submit">{{ config.testMode ? '确认并完成测试认证' : '提交学校审核' }}</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-card shadow="never"><template #header>认证记录 · 历史资料保留</template>
      <el-table :data="list" v-loading="loading"><el-table-column v-if="admin" prop="realName" label="学生姓名" /><el-table-column prop="identityNo" label="学号" /><el-table-column label="认证方式" width="150"><template #default="{row}">{{ row.verificationMode==='TEST' ? '内部测试认证' : '学校人工审核' }}</template></el-table-column><el-table-column label="状态" min-width="160"><template #default="{row}"><el-tag>{{ stateLabel(row) }}</el-tag></template></el-table-column><el-table-column prop="opinion" label="说明 / 审核意见" min-width="240" /><el-table-column v-if="admin" label="操作" width="240"><template #default="{row}"><el-button v-if="row.fileId" link type="primary" @click="download(row.fileId)">查看原证明</el-button><template v-if="row.status===1 && row.verificationMode!=='TEST'"><el-button link type="primary" @click="audit(row,true)">通过</el-button><el-button link type="danger" @click="audit(row,false)">驳回</el-button></template><span v-if="row.verificationMode==='TEST'">无需人工审核</span></template></el-table-column></el-table>
      <div v-if="admin" class="table-pagination"><el-pagination v-model:current-page="current" :page-size="20" layout="prev,pager,next" :total="hasMore?current*20+1:current*20" @current-change="load" /></div>
    </el-card>
  </div>
</template>
<script>
import request from '@/utils/request'
import { uploadFile,downloadFile } from '@/api/file'
export default {
  name:'CampusIdentity',
  data:()=>({list:[],loading:false,uploading:false,busy:false,current:1,hasMore:false,config:{testMode:false},configLoaded:false,fileList:[],form:{identityNo:'',realName:'',college:'',fileId:null,acknowledged:false}}),
  computed:{admin(){return this.$store.getters.role==='ADMIN'},profile(){return this.$store.getters.userInfo},identityLabel(){return this.profile.identityTestOnly ? (this.profile.authStatus===2?'内部测试认证已完成':'测试认证已停用，需学校审核') : ({0:'尚未认证',1:'待确认 / 待学校审核',2:'校园身份已认证',3:'学校审核已驳回'}[this.profile.authStatus]||'尚未认证')}},
  async created(){await this.load();this.form={...this.form,identityNo:this.profile.identityNo||'',realName:this.profile.realName||'',college:this.profile.college||''}},
  methods:{
    stateLabel(row){return row.status===2&&row.verificationMode==='TEST'?'测试认证已完成':({1:'待学校审核',2:'学校审核已通过',3:'已驳回',4:'已转测试认证，资料保留'}[row.status]||'未知状态')},
    async load(){this.loading=true;try{const [c,r]=await Promise.all([request({url:'/user/identity/config'}),request({url:this.admin?'/admin/identities':'/user/identity',params:this.admin?{current:this.current,size:20}:undefined}),this.$store.dispatch('user/fetchProfile')]);this.config=c.data;this.configLoaded=true;this.list=r.data||[];this.hasMore=this.list.length===20}finally{this.loading=false}},
    async upload(file){this.uploading=true;try{const r=await uploadFile(file.raw,'IDENTITY');this.form.fileId=r.data.fileId}catch(e){this.form.fileId=null;this.fileList=[]}finally{this.uploading=false}},
    async submit(){if(this.busy||!this.configLoaded)return;if(!this.form.identityNo.trim())return this.$message.warning('请填写学号');if(this.config.testMode&&(!this.form.realName.trim()||!this.form.college.trim()||!this.form.acknowledged))return this.$message.warning('请填写姓名、学校/院系并确认测试声明');if(!this.config.testMode&&!this.form.fileId)return this.$message.warning('请上传校园身份证明');this.busy=true;try{await request({url:'/user/identity',method:'post',data:this.form});await this.load();this.$message.success(this.config.testMode?'测试认证已完成，现在可以创建捐赠订单':'已提交学校审核')}finally{this.busy=false}},
    async download(id){try{await downloadFile(id)}catch(e){this.$message.error(e.message)}},
    async audit(row,pass){try{const {value}=await this.$prompt(pass?'确认已核对校园身份证明，可填写意见':'请填写驳回原因','校园身份审核',{inputValidator:v=>pass||!!(v&&v.trim())||'请填写原因'});await request({url:`/admin/identities/${row.reviewId}/audit`,method:'post',data:{pass,opinion:value||''}});this.$message.success('身份审核已完成');await this.load()}catch(e){if(e!=='cancel'&&e!=='close')console.error(e)}}
  }
}
</script>
<style scoped>.identity-current{display:flex;align-items:center;gap:16px;margin-bottom:24px}.identity-workspace :deep(.el-checkbox){height:auto;white-space:normal}</style>

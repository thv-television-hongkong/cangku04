const path = require('path')
const fs = require('fs')
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const origin = process.env.SYSTEM_URL || 'http://127.0.0.1:9528'
const output = path.resolve(__dirname, process.env.VERIFICATION_OUTPUT || '../docs/verification-20261017')
fs.mkdirSync(output, { recursive: true })
;(async () => {
  const browser = await chromium.launch({ headless: true, executablePath:process.env.BROWSER_EXECUTABLE || undefined })
  const findings = []
  for (const user of [
    {account:'admin', routes:['/admin/audit','/admin/finance','/admin/refunds','/admin/evidence-tasks','/admin/identities','/admin/statistics','/admin/users','/admin/system']},
    {account:'20210001', routes:['/donor/projects','/donor/orders','/donor/donations','/donor/refunds','/donor/identity','/trace/query','/profile']},
    {account:'club001', routes:['/club/projects','/club/project/form','/club/qualification']},
    {account:'auditor', routes:['/auditor/projects','/auditor/orders','/auditor/refunds','/auditor/reports','/auditor/fund-trace','/auditor/rules']}
  ]) {
    const context = await browser.newContext({ viewport: { width:1440, height:960 } })
    const page = await context.newPage()
    const errors = []
    page.on('pageerror', e => errors.push(e.message))
    page.on('response', r => { if(r.status()>=400 && r.url().includes('/api/')) errors.push(`${r.status()} ${new URL(r.url()).pathname}`) })
    await page.goto(`${origin}/#/login`)
    await page.locator('input').first().fill(user.account)
    await page.locator('input[type=password]').first().fill('123456')
    await page.getByRole('button', { name: '登录', exact:true }).click()
    await page.waitForURL(url => !url.hash.startsWith('#/login'), {timeout:20000})
    for(const route of user.routes) {
      await page.goto(`${origin}/#${route}`)
      await page.waitForTimeout(900)
      findings.push({ account:user.account, route, url:page.url(), body:(await page.locator('body').innerText()).slice(0,180), errors:[...errors] })
      if(['/admin/finance','/donor/orders','/admin/statistics'].includes(route)) await page.screenshot({path:path.join(output,`${user.account}-${route.replaceAll('/','-')}.png`),fullPage:true})
    }
    await context.close()
  }
  const mobile = await browser.newContext({ viewport:{width:390,height:844}, isMobile:true })
  const page = await mobile.newPage()
  await page.goto(`${origin}/#/login`)
  await page.screenshot({path:path.join(output,'login-mobile.png'),fullPage:true})
  await mobile.close()
  await browser.close()
  fs.writeFileSync(path.join(output,'browser-smoke.json'),JSON.stringify(findings,null,2))
  const failures=findings.filter(f=>f.errors.length||f.url.includes('/404')||f.url.includes('/login'))
  console.log(JSON.stringify({pages:findings.length,failures},null,2))
  if(failures.length) process.exitCode=1
})().catch(e=>{console.error(e);process.exitCode=1})

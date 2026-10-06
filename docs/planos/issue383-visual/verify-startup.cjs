const fs=require('fs');
const path=require('path');
const {pathToFileURL}=require('url');
const {chromium}=require(path.join(process.argv[2],'playwright'));
const before=process.argv[3]==='before';
const preview=path.resolve(process.argv[4]);
const directory=path.resolve(process.argv[5]);
(async()=>{
  fs.mkdirSync(directory,{recursive:true});
  const browser=await chromium.launch({headless:true,channel:'msedge'});
  try {
    const context=await browser.newContext({viewport:{width:1080,height:2200},colorScheme:'dark'});
    await context.route('https://**/*',route=>route.abort());
    const page=await context.newPage();
    const errors=[];
    page.on('pageerror',error=>errors.push(error.message));
    await page.goto(pathToFileURL(preview).href);
    const frame=page.frames().find(frame=>frame!==page.mainFrame());
    await frame.waitForTimeout(100);
    const count=await frame.locator('.hm-product').count();
    if(before) {
      if(count!==0)throw new Error('A versão antiga não reproduziu a tela vazia.');
      fs.writeFileSync(path.join(directory,'before.json'),JSON.stringify({blockedExternalScripts:true,count,errors},null,2));
      console.log(JSON.stringify({mode:'before',blockedExternalScripts:true,count,errors}));
      return;
    }
    if(count!==10||errors.length)throw new Error(JSON.stringify({count,errors}));
    const active=frame.locator('[data-variant]:not([hidden])');
    await active.getByRole('button',{name:'7d',exact:true}).click();
    await active.locator('.hm-metrics').getByText('41 %',{exact:true}).waitFor();
    await active.locator('summary').filter({hasText:'Janelas e distribuição horária'}).click();
    await active.getByText('8 mais recentes de 10 · BRT',{exact:true}).waitFor();
    await active.getByRole('button',{name:'Relatório PDF',exact:true}).click();
    await active.getByText('Prévia do relatório · nenhuma gravação realizada',{exact:true}).waitFor();
    await active.locator('.hm-product').screenshot({path:path.join(directory,'fixed-offline.png')});
    const withoutScripts=await browser.newContext({javaScriptEnabled:false,viewport:{width:1080,height:2200},colorScheme:'dark'});
    await withoutScripts.route('https://**/*',route=>route.abort());
    const staticPage=await withoutScripts.newPage();
    await staticPage.goto(pathToFileURL(path.resolve(__dirname,'index.html')).href);
    const staticCount=await staticPage.locator('.hm-product').count();
    if(staticCount!==10)throw new Error(`HTML sem JS não contém dez telas: ${staticCount}`);
    await staticPage.locator('[data-variant]:not([hidden]) .hm-product').screenshot({path:path.join(directory,'fixed-without-javascript.png')});
    const review=await context.newPage();
    review.on('pageerror',error=>errors.push(error.message));
    await review.goto(pathToFileURL(path.resolve(__dirname,'index.html')).href);
    await review.waitForFunction(()=>document.querySelectorAll('#review-direction option').length===10);
    await review.locator('#review-direction').selectOption('4');
    await review.locator('#review-data').selectOption('Atividade local');
    await review.locator('[data-variant]:not([hidden])').getByRole('button',{name:'modelo-local-8',exact:true}).click();
    await review.locator('[data-variant]:not([hidden]) .hm-metric-rows').getByText('192 req',{exact:true}).waitFor();
    if(errors.length)throw new Error(JSON.stringify(errors));
    const result={networkBlocked:true,errors,interactiveScreens:count,staticScreens:staticCount,checks:['cota semanal','expansão','prévia de PDF','HTML sem JavaScript','seletor standalone','modelo oito']};
    fs.writeFileSync(path.join(directory,'fixed.json'),JSON.stringify(result,null,2));
    console.log(JSON.stringify(result));
  } finally {await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});

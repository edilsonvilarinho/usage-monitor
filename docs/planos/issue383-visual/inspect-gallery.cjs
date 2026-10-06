// Inspeção de conteúdo, interações e geometria da galeria de propostas.
const fs=require('fs');
const path=require('path');
const {pathToFileURL}=require('url');
const runtime=process.argv[2];
const preview=process.argv[3];
const destination=process.argv[4];
const {chromium}=require(path.join(path.resolve(runtime),'playwright'));
(async()=>{
  fs.mkdirSync(destination,{recursive:true});
  const browser=await chromium.launch({headless:true,channel:'msedge'});
  const page=await browser.newPage({viewport:{width:1080,height:1200},deviceScaleFactor:1});
  const errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.goto(pathToFileURL(path.resolve(preview)).href);
  const frame=page.frames().find(f=>f!==page.mainFrame());
  await frame.waitForSelector('.hm-product');
  await frame.waitForFunction(()=>document.querySelectorAll('.hm-product').length===10);
  await frame.evaluate(()=>document.fonts.ready);
  const scenarios=[];
  for(const width of [1030,680,320]) {
    await page.setViewportSize({width:width+48,height:1200});
    for(const tema of ['Escuro','Claro']) {
      for(const dados of ['Cotas','Atividade local','Sem dados']) {
        for(let variant=0;variant<10;variant++) {
          await frame.evaluate(({variant,tema,dados})=>{
            Object.assign(issue383Gallery.settings,{tema,dados,largura:'Normal'});
            issue383Gallery.applySettings();
            const all=document.querySelectorAll('[data-variant]');
            all.forEach((s,i)=>{s.hidden=i!==variant;});
          },{variant,tema,dados});
          await frame.waitForTimeout(40);
          const measurement=await frame.evaluate(()=>{
            const p=document.querySelector('[data-variant]:not([hidden]) .hm-product');
            const b=p.getBoundingClientRect();
            const overflow=[...p.querySelectorAll('*')].filter(e=>{
              const r=e.getBoundingClientRect();
              return r.width>0&&r.right>b.right+2&&!e.closest('table');
            }).map(e=>e.tagName+':'+e.textContent.slice(0,70));
            const font=getComputedStyle(p).fontFamily;
            return {width:b.width,overflow:overflow.slice(0,10),font};
          });
          scenarios.push({width,tema,dados,variant:variant+1,...measurement});
          if((width===1030&&tema==='Escuro'&&dados==='Cotas')||(width===320&&tema==='Claro'&&dados==='Atividade local'&&[0,4,7,9].includes(variant))) {
            await page.setViewportSize({width:width+48,height:4000});
            await page.locator('iframe').evaluate(element=>element.style.height='10000px');
            await frame.locator('[data-variant]:not([hidden]) .hm-product').screenshot({path:path.join(destination,`proposal-${variant+1}-${width}-${tema}-${dados.replace(/ /g,'-')}.png`)});
          }
        }
      }
    }
  }
  // Interações das propostas: abas, cota, expansão, seleção de modelo e prévia.
  await page.setViewportSize({width:1080,height:1200});
  await frame.evaluate(()=>{
    Object.assign(issue383Gallery.settings,{tema:'Escuro',dados:'Cotas'});issue383Gallery.applySettings();
    document.querySelectorAll('[data-variant]').forEach((s,i)=>s.hidden=i!==1);
  });
  let active=frame.locator('[data-variant]:not([hidden])');
  await active.getByRole('tab',{name:'Janelas',exact:true}).click();
  await active.getByText('8 mais recentes de 10 · BRT',{exact:true}).waitFor();
  await active.getByRole('button',{name:'Ver todas',exact:true}).click();
  await active.getByText('10 janelas no intervalo',{exact:true}).waitFor();
  await active.getByRole('button',{name:'Relatório PDF',exact:true}).click();
  await active.getByText('Prévia do relatório · nenhuma gravação realizada',{exact:true}).waitFor();
  await frame.evaluate(()=>{
    Object.assign(issue383Gallery.settings,{dados:'Sem dados'});issue383Gallery.applySettings();
  });
  await frame.waitForFunction(()=>document.querySelector('[data-variant]:not([hidden]) button[disabled]')!=null);
  await frame.evaluate(()=>{
    Object.assign(issue383Gallery.settings,{dados:'Atividade local'});issue383Gallery.applySettings();
    document.querySelectorAll('[data-variant]').forEach((s,i)=>s.hidden=i!==4);
  });
  active=frame.locator('[data-variant]:not([hidden])');
  await active.getByRole('button',{name:'modelo-local-8',exact:true}).click();
  await active.locator('.hm-metric-rows').getByText('192 req',{exact:true}).waitFor();
  await frame.evaluate(()=>{
    Object.assign(issue383Gallery.settings,{dados:'Cotas'});issue383Gallery.applySettings();
    document.querySelectorAll('[data-variant]').forEach((s,i)=>s.hidden=i!==0);
  });
  active=frame.locator('[data-variant]:not([hidden])');
  await active.getByRole('button',{name:'7d',exact:true}).click();
  await active.locator('.hm-metrics').getByText('41 %',{exact:true}).waitFor();
  await active.getByRole('button',{name:'Ampliar trecho',exact:true}).click();
  await active.getByRole('button',{name:'Ver intervalo inteiro',exact:true}).waitFor();
  await active.locator('summary').filter({hasText:'Janelas e distribuição horária'}).click();
  await active.getByText('8 mais recentes de 10 · BRT',{exact:true}).waitFor();
  const report={errors,scenarioCount:scenarios.length,overflow:scenarios.filter(s=>s.overflow.length),interactionChecks:['abas','todas as janelas','prévia de PDF','PDF indisponível sem dados','modelo 8','cota 7d','zoom','expansão'],scenarios};
  fs.writeFileSync(path.join(destination,'inspection.json'),JSON.stringify(report,null,2));
  console.log(JSON.stringify({errors,scenarios:scenarios.length,overflowScenarios:report.overflow.length,examples:report.overflow.slice(0,5),interactionChecks:report.interactionChecks}));
  await browser.close();
  if(errors.length||report.overflow.length)process.exitCode=1;
})().catch(e=>{console.error(e);process.exit(1);});

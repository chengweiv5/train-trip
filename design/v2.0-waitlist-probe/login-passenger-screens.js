function account(p,logged=true){const b=row(p,'12306账号',{padding:14,cornerRadius:12,fill:C.card});icon(b,'shield-check');const c=frame(b,'账号状态',{gap:2});text(c,logged?'12306 已登录':'登录 12306',16,C.ink,'600');text(c,logged?'账号尾号 2086':'同步账号下可用的乘车人',12,C.muted);if(logged)text(b,'切换',13,C.primary,'600',{textGrowth:'auto'});}
function person(p,name,tail,selected=true,valid=true){const r=row(p,name+'乘车人',{padding:[12,0],gap:12});icon(r,selected?'circle-check':valid?'circle-help':'clock',selected?C.primary:C.muted,22);const c=frame(r,'乘客资料',{gap:4});const h=row(c,'姓名与票种');text(h,name,16,C.ink,'600');text(h,'成人票',12,C.muted,'normal',{textGrowth:'auto'});text(c,'居民身份证 · 尾号 '+tail,12,C.muted);text(c,valid?'已核验':'待核验，暂不可选',12,valid?C.muted:C.warn);}
function tripSummary(p){const c=frame(p,'候补行程',{padding:16,gap:12,cornerRadius:12,fill:C.card,stroke:C.border,strokeWidth:1});const r=row(c,'车次与日期');text(r,'G374',22,C.ink,'bold');text(r,'10月7日 周三',13,C.muted,'normal',{textAlign:'right'});const t=row(c,'出发到达');const a=frame(t,'出发',{gap:2});text(a,'08:20',26,C.ink,'600');text(a,'洛阳龙门',13,C.muted);const mid=frame(t,'行程时间',{gap:4});text(mid,'3小时10分',12,C.muted,'normal',{textAlign:'center'});line(mid);const b=frame(t,'到达',{gap:2});text(b,'11:30',26,C.ink,'600',{textAlign:'right'});text(b,'北京西',13,C.muted,'normal',{textAlign:'right'});line(c);text(c,'二等座 · 5 位乘车人',15,C.ink,'600');return c;}
screens=[];
{
const {s,body}=start('01 登录入口','先选乘车人','登录后选择本次一起候补的乘车人',0);
account(body,false);const b=frame(body,'连接账号说明',{gap:18,padding:[16,0]});text(b,'一次选择，保留同行名单',20,C.ink,'600');text(b,'读取 12306 账号中的乘车人，选择这次同行的人。人数随勾选自动计算。',15,C.muted);const steps=frame(b,'使用步骤',{gap:18});for(const [n,label] of [['1','完成 12306 登录'],['2','同步并勾选乘车人'],['3','选择车次与席别']]){const r=row(steps,label,{gap:12});const d=row(r,'步骤 '+n,{width:32,height:32,justifyContent:'center',fill:C.header,cornerRadius:10});text(d,n,14,C.primary,'600',{textGrowth:'auto'});text(r,label,15,C.ink);}button(body,'登录 12306','primary','external-link');text(body,'完成登录后返回，继续选择乘车人。',12,C.muted);end(s);
}
{
const {s,body}=start('02 勾选5位乘车人','选择乘车人','人数由实际勾选计算',1,{back:true});account(body);const h=row(body,'同步状态');text(h,'刚刚同步 · 共 6 位',12,C.muted);text(h,'刷新',13,C.primary,'600',{textGrowth:'auto'});const list=frame(body,'账号乘车人列表',{gap:0});for(const [i,n,t] of [[0,'陈*','1028'],[1,'李*','2046'],[2,'王*','3185'],[3,'赵*','4067'],[4,'周*','5290'],[5,'林*','6301']]){if(i)line(list);person(list,n,t,i<5,i<5);}note(body,'已选择 5 位乘车人','名单变更后，需要按新的同行人员重新核对。','neutral','users');button(body,'使用这 5 位乘车人','primary');end(s,false);
}
{
const {s,body}=start('03 候补条件就绪','准备候补','已选择 5 位乘车人',2,{back:true});tripSummary(body);const c=frame(body,'同行名单',{padding:14,cornerRadius:12,fill:C.neutral,gap:8});const r=row(c,'名单标题');text(r,'同行乘车人',15,C.ink,'600');text(r,'修改',13,C.primary,'600',{textGrowth:'auto'});text(c,'陈*、李*、王*、赵*、周*',14,C.muted);field(body,'截止兑现时间','开车前 2 小时');note(body,'本席别可加入候补列表','是否接受这 5 位乘车人的订单，以提交时的结果为准。');text(body,'接下来会发生什么',18,C.ink,'600');text(body,'继续后先查看提交说明，确认后才发送候补请求。',14,C.muted);button(body,'查看提交说明','primary');button(body,'返回选择其他车次','secondary');end(s,false);
}
{
const {s,body}=start('04 正式提交前确认','确认候补请求','请核对本次行程和乘车人',3,{back:true});tripSummary(body);text(body,'乘车人：陈*、李*、王*、赵*、周*',14,C.ink);text(body,'截止兑现：开车前 2 小时',13,C.muted);note(body,'成功时会生成待支付订单','本操作会向 12306 提交真实候补请求，不是仅查询名额。','warn','info');const c=frame(body,'操作说明',{gap:14});text(c,'本次只提交所选车次、席别及 5 位乘车人的请求。',14,C.ink);text(c,'生成订单后显示待支付信息，由你决定后续处理。',14,C.muted);text(c,'本次操作不自动付款，也不自动取消订单。',14,C.muted);button(body,'尝试提交候补订单','primary');button(body,'暂不提交','secondary');end(s,false);
}
{
const {s,body}=start('05 已创建待支付订单','候补订单已创建','请在 12306 核对并处理订单',4,{back:true});note(body,'订单已创建 · 待支付','这是提交结果，还没有完成支付。','good','circle-check');tripSummary(body);const c=frame(body,'订单信息',{gap:0});field(c,'订单号','以返回结果为准','info');line(c);field(c,'预付款','以返回金额为准','info');line(c);field(c,'支付时限','以返回时间为准','clock');text(body,'后续候补兑现情况以 12306 为准。',13,C.muted);button(body,'打开 12306 处理订单','primary','external-link');button(body,'返回候补','secondary');end(s,false);
}
{
const {s,body}=start('06 提交超时结果未知','正在核对订单','网络超时，提交结果尚未确认',5,{back:true});tripSummary(body);note(body,'请求可能已经被受理','先查询订单状态，确认是否生成订单。','warn','clock');const c=frame(body,'状态处理',{padding:[20,0],gap:14});text(c,'先核对，再决定下一步',20,C.ink,'600');text(c,'网络超时不代表候补失败。订单状态明确前，保留本次行程和乘车人选择。',14,C.muted);button(body,'重新查询订单状态','primary','rotate-cw');button(body,'打开 12306 查看订单','secondary','external-link');end(s,false);
}
{
const {s,body}=start('07 明确拒绝的提交结果','本次提交被拒绝','12306 返回的业务提示',6,{back:true});note(body,'所选车次候补人数过多','本次提交未获接受，请查看其他车次或席别。','warn','circle-x');tripSummary(body);text(body,'保留这 5 位乘车人',18,C.ink,'600');text(body,'更换车次时沿用同行名单。每次正式提交前都会展示完整条件，由你确认。',14,C.muted);button(body,'选择其他车次','primary');button(body,'修改同行乘车人','secondary');end(s,false);
}
Print('screens',screens);

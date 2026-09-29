function link(p,label,ic){const r=row(p,label,{width:'fit_content',height:48,padding:[8,0]});if(ic)icon(r,ic);text(r,label,13,C.primary,'600',{textGrowth:'auto'});return r;}
function card(p,name,o={}){return frame(p,name,{padding:16,gap:12,cornerRadius:12,fill:C.card,stroke:C.border,strokeWidth:1,...o});}
function headerRow(p,title,action){const r=row(p,title,{height:48});text(r,title,16,C.ink,'600');if(action)link(r,action);return r;}
function locationRow(p,label,city,stations){const r=row(p,label+'城市与车站入口',{height:76,gap:12});const left=frame(r,label,{gap:3});text(left,label,12,C.muted);text(left,city,26,C.ink,'600');text(r,stations,13,C.muted);icon(r,'chevron-right',C.muted,18);return r;}
function route(p){const c=card(p,'行程查询条件');locationRow(c,'出发地','洛阳','同城各站');line(c);locationRow(c,'目的地','北京','同城各站');line(c);field(c,'出发日期','10月7日 周三');line(c);headerRow(c,'出发时段 · 可多选','自定义');const t=row(c,'时段选择',{gap:6});for(const n of ['全天','凌晨','早上','下午','晚上'])chip(t,n,n==='全天');return c;}
function querySummary(p,period='全天'){const r=card(p,'查询摘要',{padding:[10,14],gap:4});text(r,'洛阳 → 北京',18,C.ink,'600');text(r,'10月7日 · '+period+' · 同城各站',13,C.muted);return r;}
function tag(p,label,kind='primary'){const c=row(p,label,{width:'fit_content',padding:[3,8],cornerRadius:6,fill:kind==='primary'?C.header:kind==='warn'?C.warnBg:C.neutral});text(c,label,12,kind==='primary'?C.primary:kind==='warn'?C.warn:C.muted,'600',{textGrowth:'auto'});}
function seat(p,label,{selected=false,restricted=false}={}){const r=row(p,label,{height:50,padding:[10,8],gap:6,cornerRadius:8,fill:selected?C.header:restricted?C.neutral:C.white,stroke:selected?C.primary:restricted?C.neutral:C.line,strokeWidth:selected?1.5:1});icon(r,selected?'square-check':'square',restricted?C.muted:C.primary,18);text(r,label,13,restricted?C.muted:C.primary,selected?'600':'normal');}
function timing(p,dep,arr,from,duration){const r=row(p,'出发和到达',{gap:8});const a=frame(r,'出发',{gap:2});text(a,dep,26,C.ink,'600');text(a,from,13,C.muted);const c=frame(r,'车程',{gap:4});text(c,duration,12,C.muted,'normal',{textAlign:'center'});line(c);text(c,'当日到达',12,C.muted,'normal',{textAlign:'center'});const b=frame(r,'到达',{gap:2});text(b,arr,26,C.ink,'600',{textAlign:'right'});text(b,'北京西',13,C.muted,'normal',{textAlign:'right'});}
function trainCard(p,code,dep,arr,from,duration,{selected=false,restricted=false}={}){const c=card(p,code+'车次',{padding:14,gap:12,stroke:selected?C.primary:C.border,strokeWidth:selected?1.5:1});const h=row(c,'车次标题');text(h,code,18,C.ink,'bold');tag(h,selected?'已选二等座':'10月7日',selected?'primary':'neutral');timing(c,dep,arr,from,duration);line(c);const r=row(c,'选择席别',{gap:8});seat(r,'二等座',{selected,restricted});if(code==='D30'){const more=frame(r,'其他席别入口',{height:50,justifyContent:'center'});link(more,'更多席别');}else seat(r,'一等座');text(c,restricted?'二等座候补订单较多，暂不可选':'可加入候补 · 点击席别勾选',12,restricted?C.warn:C.muted);return c;}
function selectedItem(p,code,dep,arr,dur){const c=card(p,code+'已选需求',{padding:14,gap:8});const h=row(c,'车次与席别');text(h,code,17,C.ink,'600');tag(h,'二等座');text(c,`10月7日  ${dep}—${arr} · ${dur}`,14,C.ink);text(c,'洛阳龙门 → 北京西',13,C.muted);return c;}
function requestSummary(p,people=5){const c=card(p,'本次候补需求',{gap:8});text(c,'10月7日 · 洛阳龙门 → 北京西',15,C.ink,'600');text(c,'G374 / G652 / G654 · 二等座',14,C.primary,'600');text(c,people?'共 3 项候补需求 · '+people+' 位乘车人':'已选 3 项候补需求',13,C.muted);return c;}
function account(p,logged=true){const r=card(p,'12306账号',{padding:[10,14],layout:'horizontal',alignItems:'center'});icon(r,'shield-check');const c=frame(r,'账号状态',{gap:2});text(c,logged?'12306 已登录':'登录 12306',16,C.ink,'600');text(c,logged?'账号尾号 2086':'选好的车次会保留',12,C.muted);if(logged)link(r,'切换');}
function person(p,name,tail,selected=true,valid=true){const r=row(p,name,{padding:[10,0],gap:12});icon(r,selected?'square-check':valid?'square':'clock',selected?C.primary:C.muted,22);const c=frame(r,'乘车人信息',{gap:2});const h=row(c,'姓名和票种');text(h,name,16,C.ink,'600');text(h,'成人票',12,C.muted,'normal',{textGrowth:'auto'});text(c,'居民身份证 · 尾号 '+tail,12,C.muted);text(c,valid?'已核验':'待核验，暂不可选',12,valid?C.muted:C.warn);}
function names(p,edit=true){const c=frame(p,'同行乘车人',{gap:4});headerRow(c,'乘车人 · 5 位',edit?'修改':null);text(c,'陈晨、李晓、王宁、赵宇、周明',14,C.ink);text(c,'5 张成人票',12,C.muted);return c;}
function actions(s,summary,primary,secondary){const c=frame(s,'底部操作',{padding:[12,16,16,16],gap:8,fill:C.white,stroke:C.line,strokeWidth:{top:1}});if(summary)text(c,summary,13,C.muted);button(c,primary);if(secondary)link(c,secondary);return c;}

function stats(p,entries){const r=row(p,'处理统计',{gap:8});for(const [num,label,kind] of entries){const c=card(r,label,{padding:[12,8],alignItems:'center',gap:3,fill:kind==='good'?C.goodBg:kind==='warn'?C.warnBg:C.neutral,stroke:kind==='good'?C.goodBg:kind==='warn'?C.warnBg:C.neutral});text(c,String(num),27,kind==='good'?C.good:kind==='warn'?C.warn:C.ink,'bold',{textAlign:'center'});text(c,label,12,kind==='good'?C.good:kind==='warn'?C.warn:C.muted,'normal',{textAlign:'center'});}}
function removed(p,code,round,time){const c=frame(p,code+'排除记录',{gap:6,padding:[12,0]});const r=row(c,'处理结果');icon(r,'circle-x',C.warn,18);text(r,code+' · 二等座',15,C.ink,'600');text(r,'已排除',12,C.warn,'600',{textGrowth:'auto'});text(c,'10月7日 · 洛阳龙门 → 北京西',12,C.muted);text(c,'12306 提示：'+code+' 二等座候补人数过多',13,C.warn);text(c,'第 '+round+' 次提交 · '+time,12,C.muted);}
function inOrder(p){const c=card(p,'最终入单需求',{gap:8});const r=row(c,'已入单车次');text(r,'G654 · 二等座',18,C.ink,'600');tag(r,'已入订单');text(c,'10月7日 09:10—12:50',14,C.ink);text(c,'洛阳龙门 → 北京西 · 5 位乘车人',13,C.muted);return c;}

screens=[];
{
const {s,body}=start('01 全部查询与筛选条件','选好车次，自动继续','遇到候补过多，自动排除后继续提交',0);const a=row(body,'页面快捷入口');text(a,'候补车票',16,C.ink,'600');link(a,'我的候补');route(body);const c=card(body,'其他查询条件',{gap:0});field(c,'席别','二等座、一等座');line(c);field(c,'车种','不限');line(c);field(c,'最长车程','不限');line(c);field(c,'结果排序','出发最早');line(c);const toggle=row(c,'只看可加入候补',{height:56});text(toggle,'只看可加入候补',15,C.ink);icon(toggle,'square-check',C.primary,22);text(c,'默认开启 · 隐藏所选席别中不可候补的选项。',12,C.muted);button(body,'查询候补车次','primary','search');text(body,'选好车次后，登录并选择乘车人。',12,C.muted);end(s);
}
{
const {s,body}=start('02 车次结果与多选','洛阳 → 北京','10月7日 · 全天 · 同城各站',1,{back:true});const summary=card(body,'已应用条件摘要',{padding:[10,14],gap:3});text(summary,'二等座、一等座 · 全部车种 · 车程不限',13,C.muted);text(summary,'只看可加入候补 · 出发最早排序',12,C.muted);const tools=row(body,'查询结果操作',{justifyContent:'space_between'});text(tools,'3 趟匹配车次 · 09:41 更新',12,C.muted);link(tools,'修改条件','sliders-horizontal');trainCard(body,'G374','08:20','11:30','洛阳龙门','3小时10分',{selected:true});trainCard(body,'G652','08:25','12:13','洛阳龙门','3小时48分',{selected:true});trainCard(body,'G654','09:10','12:50','洛阳龙门','3小时40分',{selected:true});text(body,'可加入候补，提交时仍需以 12306 结果为准。',12,C.muted);actions(s,'已选 3 项需求 · 下一步选择乘车人','下一步 · 选乘车人','查看已选需求');end(s,false);
}
{
const {s,body}=start('03 登录并保留需求','登录 12306','登录后读取账号下可用的乘车人',2,{back:true});requestSummary(body,0);account(body,false);const c=frame(body,'登录说明',{padding:[24,0],gap:14});text(c,'车次已选好，继续选人',22,C.ink,'600');text(c,'完成 12306 登录后返回这里，选择这次一起出行的乘车人。',15,C.muted);text(c,'已选需求会保留。',13,C.muted);button(body,'登录 12306','primary','external-link');button(body,'先返回查看车次','secondary');end(s,false);
}
{
const {s,body}=start('04 选择乘车人','选择乘车人','为本次候补需求选择同行人员',3,{back:true});account(body);headerRow(body,'账号乘车人 · 6 位','刷新');const c=frame(body,'乘车人列表',{gap:0});for(const [i,n,t] of [[0,'陈晨','1028'],[1,'李晓','2046'],[2,'王宁','3185'],[3,'赵宇','4067'],[4,'周明','5290'],[5,'林悦','6301']]){if(i)line(c);person(c,n,t,i<5,i<5);}link(body,'去 12306 管理乘车人','external-link');actions(s,'已选 5 位 · 共 5 张成人票','下一步 · 确认候补');end(s,false);
}
{
const {s,body}=start('05 一次确认自动提交','确认自动候补','核对全部候选，系统自动处理受限项',4,{back:true});headerRow(body,'候选需求 · 3 项','修改');selectedItem(body,'G374','08:20','11:30','3小时10分');selectedItem(body,'G652','08:25','12:13','3小时48分');selectedItem(body,'G654','09:10','12:50','3小时40分');names(body);line(body);field(body,'截止兑现时间','10月7日 06:20');note(body,'自动排除，继续提交','首轮一起提交全部需求。移除明确被拒绝项后，再一起提交全部剩余需求，直到订单创建或全部失败。','neutral','rotate-cw');text(body,'全程使用这 5 位乘车人，成功后在 12306 支付。',12,C.muted);link(body,'查看候补购票规则','info');actions(s,'3 项候选 · 5 位乘车人','开始自动提交');end(s,false);
}
{
const {s,body}=start('06 全部剩余需求一起提交','正在自动提交','每轮一起提交全部剩余需求',5,{back:true});stats(body,[[3,'最初选择','neutral'],[1,'已排除','warn'],[2,'剩余需求','neutral']]);const active=card(body,'当前提交集合',{gap:12});const h=row(active,'轮次');text(h,'第 2 次提交',19,C.primary,'bold');tag(h,'处理中');text(active,'本轮一起提交以下 2 项',15,C.ink,'600');text(active,'G652 · 二等座\nG654 · 二等座',17,C.primary,'600');text(active,'10月7日 · 洛阳龙门 → 北京西',13,C.muted);text(active,'5 位乘车人 · 等待本轮提交结果',13,C.muted);headerRow(body,'自动处理记录','查看全部');removed(body,'G374',1,'09:41:02');note(body,'继续提交全部剩余项','首轮一起提交 3 项；G374 被拒绝后，移除它，本轮一起提交 G652 和 G654。','neutral','info');button(body,'停止继续提交','secondary');end(s,false);
}
{
const {s,body}=start('07 自动处理后创建订单','候补订单已创建','自动提交已结束，请前往支付',6,{back:true});note(body,'已提交成功 · 待支付','自动排除了 2 项受限需求，G654 已进入订单。','good','circle-check');inOrder(body);const c=card(body,'待支付信息',{gap:6,fill:C.goodBg,stroke:C.goodBg});text(c,'待付预付款',13,C.good);text(c,'¥ 980.00',32,C.ink,'600');text(c,'请在今天 10:11 前支付',14,C.good,'600');text(c,'示例订单 DEMO-1007-01',12,C.muted);names(body,false);link(body,'查看自动处理记录 · 已排除 2 项');text(body,'订单创建成功不代表候补已兑现。',12,C.muted);button(body,'打开 12306 支付','primary','external-link');button(body,'刷新订单状态','secondary','rotate-cw');end(s,false);
}
{
const {s,body}=start('08 所有候选均被拒绝','本次候补未提交成功','3 项候选均已收到明确拒绝',7,{back:true});stats(body,[[3,'已排除','warn'],[0,'已入单','neutral'],[0,'待确认','neutral']]);note(body,'全部候选已处理完','本次没有创建候补订单，同行名单已保留。','warn','circle-x');removed(body,'G374',1,'09:41:02');line(body);removed(body,'G652',2,'09:41:05');line(body);removed(body,'G654',3,'09:41:08');button(body,'重新选择车次','primary');text(body,'再次开始时会重新查询，不把本次拒绝永久记为不可候补。',12,C.muted);end(s,false);
}
{
const {s,body}=start('09 超时暂停并核对订单','正在核对订单','自动提交已暂停，保留处理进度',8,{back:true});stats(body,[[3,'最初选择','neutral'],[2,'已排除','warn'],[1,'待确认','neutral']]);const c=card(body,'未确认的请求',{gap:8});text(c,'第 3 次提交 · G654 二等座',18,C.ink,'600');text(c,'10月7日 · 5 位乘车人',13,C.muted);note(body,'本轮请求可能已经受理','先核对订单状态。确认结果之前，不排除 G654，也不重复提交。','warn','clock');button(body,'重新查询订单状态','primary','rotate-cw');button(body,'打开 12306 查看订单','secondary','external-link');link(body,'查看已排除的 2 项');end(s,false);
}
{
const {s,body}=start('10 没有匹配结果','洛阳 → 北京','10月7日 · 早上 · 二等座',9,{back:true});querySummary(body,'早上');const c=frame(body,'空结果',{padding:[36,16],gap:16,alignItems:'center'});icon(c,'search',C.primary,42);text(c,'暂时没有符合条件的车次',20,C.ink,'600',{textAlign:'center'});text(c,'试试其他日期、时段或席别。',14,C.muted,'normal',{textAlign:'center'});note(body,'只看可加入候补已开启','当前受限的候补车次已隐藏。');button(body,'调整查询条件','primary');button(body,'查看受限车次','secondary');end(s,false);
}
{
const {s,body}=start('11 登录失效后续处理','自动提交已暂停','重新登录后可继续剩余需求',10,{back:true});stats(body,[[3,'最初选择','neutral'],[1,'已排除','warn'],[2,'剩余需求','neutral']]);note(body,'12306 登录已过期','已保留剩余 G652、G654，以及你的乘车人选择。','warn','shield-check');text(body,'继续剩余项，不用从头选',21,C.ink,'600');text(body,'重新登录后核对账号、乘车人和订单状态，再继续本次处理。',14,C.muted);button(body,'重新登录并继续','primary');button(body,'结束本次处理','secondary');end(s,false);
}
{
const {s,body}=start('12 我的候补与自动处理','我的候补','12306 账号尾号 2086',11,{back:true});const tabs=row(body,'订单筛选',{gap:6});chip(tabs,'全部',true);chip(tabs,'待支付');chip(tabs,'候补中');chip(tabs,'已结束');const c=card(body,'本次待支付订单',{gap:12});const h=row(c,'订单状态');text(h,'洛阳龙门 → 北京西',17,C.ink,'600');tag(h,'待支付','warn');text(c,'10月7日 · G654 · 二等座',14,C.ink);text(c,'5 位乘车人 · 自动排除 2 项',13,C.muted);line(c);text(c,'预付款 ¥980.00',20,C.ink,'600');text(c,'今天 10:11 前支付 · 示例订单 DEMO-1007-01',12,C.muted);button(c,'查看订单');text(body,'自动处理记录包含每轮提交集合、排除原因和最终入单项。',12,C.muted);button(body,'刷新订单','secondary','rotate-cw');end(s,false);
}
{
const {s,body}=start('13 全部已选需求','已选候补需求','共 3 项 · 首轮一起提交',12,{back:true});selectedItem(body,'G374','08:20','11:30','3小时10分');link(body,'移除 G374 二等座');selectedItem(body,'G652','08:25','12:13','3小时48分');link(body,'移除 G652 二等座');selectedItem(body,'G654','09:10','12:50','3小时40分');link(body,'移除 G654 二等座');note(body,'所有剩余需求一起提交','首轮提交全部选择。收到明确拒绝后，只移除报错项，再一起提交全部剩余项。');button(body,'继续选择车次','secondary');actions(s,'已选 3 项候补需求','下一步 · 选乘车人');end(s,false);
}

{
const {s,body}=start('14 自动处理记录','自动处理记录','3 次提交 · 1 项入单 · 2 项排除',13,{back:true});note(body,'已创建待支付订单','G654 已入单，后续自动提交已停止。','good','circle-check');const c=frame(body,'逐次记录',{gap:0});removed(c,'G374',1,'09:41:02');text(c,'继续提交：G652、G654',13,C.primary);line(c);removed(c,'G652',2,'09:41:05');text(c,'继续提交：G654',13,C.primary);line(c);const done=frame(c,'第三次提交',{padding:[14,0],gap:8});text(done,'第 3 次提交 · 09:41:08',14,C.ink,'600');text(done,'G654 二等座 · 订单已创建',15,C.good,'600');text(done,'示例订单 DEMO-1007-01',12,C.muted);text(body,'所有排除仅针对本次候补选择，乘车人始终为原来的 5 位。',12,C.muted);button(body,'查看待支付订单','primary');end(s,false);
}
{
const {s,body}=start('15 无法定位受限需求时暂停','自动提交已暂停','本次返回未指明受限车次',14,{back:true});stats(body,[[1,'已排除','warn'],[2,'暂未确定','neutral'],[0,'已入单','neutral']]);note(body,'未能识别要排除的需求','12306 提示：所选车次候补人数过多。','warn','circle-help');text(body,'保留 G652、G654',20,C.ink,'600');text(body,'无法确定是哪一项受限，未自动删除任何一项。已选需求和乘车人已保留。',14,C.muted);button(body,'查看并调整需求','primary');button(body,'打开 12306 查看','secondary','external-link');end(s,false);
}
{
const {s,body}=start('16 每轮一起提交的示例','每轮提交全部剩余项','同一批乘车人，同一份候补需求逐轮缩减',15,{back:true});const rounds=frame(body,'提交轮次',{gap:16});for(const [num,trains,response] of [['第 1 次','G374 + G652 + G654','收到 G374 二等座候补过多 → 排除 G374'],['第 2 次','G652 + G654','收到 G652 二等座候补过多 → 排除 G652'],['第 3 次','G654','创建订单 → 停止；若仍被拒绝 → 全部失败']]){const c=card(rounds,num,{gap:10});text(c,num,15,C.muted,'600');text(c,trains,18,C.primary,'bold');text(c,'以上全部需求在同一次请求中提交',12,C.muted);line(c);text(c,response,14,C.ink);}text(body,'所选组合需符合 12306 单次订单限制；超限时先调整选择。',12,C.muted);button(body,'返回自动处理记录','secondary');end(s,false);
}
{
const {s,body}=start('17 停止后核对在途请求','已停止继续提交','正在核对已发送的第 3 次请求',16,{back:true});stats(body,[[3,'最初选择','neutral'],[2,'已排除','warn'],[1,'待确认','neutral']]);note(body,'停止不等于撤销订单','G654 的请求已经发出，确认结果前请勿重新开始。','neutral','clock');text(body,'后续请求已停止',21,C.ink,'600');text(body,'本轮若创建订单，仍会显示待支付信息；停止操作不会自动取消该订单。',14,C.muted);button(body,'核对本轮订单状态','primary','rotate-cw');button(body,'返回候补列表','secondary');end(s,false);
}

Print('screens',screens);

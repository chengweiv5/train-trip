package cn.traintrip.app.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cn.traintrip.app.*

@Composable private fun SecureRailwayScreen() {
    val window = (LocalContext.current as? Activity)?.window
    DisposableEffect(window) {
        val alreadySecure = window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE) != 0
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (!alreadySecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

@Composable internal fun RailwayPassengersScreen(s: WaitlistUiState, vm: WaitlistViewModel, onLogin: () -> Unit) {
    SecureRailwayScreen()
    // Deliberately not rememberSaveable or in ViewModel state: no credentials in saved state.
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var identityLastFour by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                password = ""
                identityLastFour = ""
                smsCode = ""
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            username = ""; password = ""; identityLastFour = ""; smsCode = ""
        }
    }
    LaunchedEffect(s.account?.reference) {
        if (s.account != null) { username = ""; password = ""; identityLastFour = ""; smsCode = "" }
    }
    Column(Modifier.fillMaxSize()) {
        AppTopBar("登录与乘车人", vm::back, backTag = "waitlist-back")
        Column(Modifier.weight(1f).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ContentCard(Modifier.fillMaxWidth()) {
                Text(s.account?.displayName ?: "登录 12306 账号", style = MaterialTheme.typography.titleLarge)
                Text("原生账号登录 · 无需扫码或打开网页", color = Primary)
                Text("凭据仅从本机通过 HTTPS 发往 12306 官方服务，不经开发者服务器。登录后核验账号并读取真实乘车人。",
                    color = Muted)
                Text("实验接入，尚待真实账号验证。密码不保存；会话仅在本应用进程内。登录不提交订单。",
                    style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            if (s.account == null) {
                Text(when (s.passwordPhase) {
                    RailwayPasswordUiPhase.IDLE -> "输入账号密码，仅在手机内操作"
                    RailwayPasswordUiPhase.CHECKING -> "正在检查官方登录要求…"
                    RailwayPasswordUiPhase.SMS_REQUIRED -> "需要短信验证，请填写账号绑定证件后四位"
                    RailwayPasswordUiPhase.SMS_SENDING -> "正在请求短信验证码…"
                    RailwayPasswordUiPhase.SMS_SENT -> "短信请求已接受，请填写收到的验证码并重新输入密码"
                    RailwayPasswordUiPhase.SLIDE_REQUIRED -> "需要滑块验证，当前原生组件尚未接通"
                    RailwayPasswordUiPhase.VERIFYING -> "正在核验账号与乘车人…"
                    RailwayPasswordUiPhase.ERROR -> "登录未完成"
                    RailwayPasswordUiPhase.CONNECTED -> "会话需重新核验"
                }, color = Primary, modifier = Modifier.testTag("railway-auth-status"))
                OutlinedTextField(username, { value ->
                    if (value.length <= 128) {
                        username = value; password = ""; identityLastFour = ""; smsCode = ""
                        vm.resetPasswordForm()
                    }
                }, label = { Text("12306 账号 / 手机号 / 邮箱") },
                    enabled = !s.accountBusy, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("railway-username"))
                OutlinedTextField(password, { if (it.length <= 128) password = it },
                    label = { Text("12306 密码") }, enabled = !s.accountBusy, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().testTag("railway-password"))
                val needsSms = s.passwordPhase in setOf(RailwayPasswordUiPhase.SMS_REQUIRED,
                    RailwayPasswordUiPhase.SMS_SENDING, RailwayPasswordUiPhase.SMS_SENT)
                if (needsSms) {
                    OutlinedTextField(identityLastFour, { if (it.length <= 4) identityLastFour = it },
                        label = { Text("账号绑定证件号后四位") }, enabled = !s.accountBusy, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth().testTag("railway-identity-last-four"))
                    SecondaryButton(if (s.smsRemainingSeconds > 0) "${s.smsRemainingSeconds} 秒后可重发" else "获取短信验证码", {
                        vm.requestLoginSms(username, identityLastFour)
                        identityLastFour = ""
                    }, Modifier.testTag("railway-send-sms"),
                        !s.accountBusy && s.smsRemainingSeconds == 0 && identityLastFour.length == 4 && username.isNotBlank())
                    OutlinedTextField(smsCode, { if (it.length <= 6 && it.all(Char::isDigit)) smsCode = it },
                        label = { Text("六位短信验证码") }, enabled = !s.accountBusy, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth().testTag("railway-sms-code"))
                }
                PrimaryButton("登录并读取乘车人", {
                    vm.loginWithPassword(username, password.toCharArray(), smsCode)
                    password = ""; smsCode = ""
                }, !s.accountBusy && username.isNotBlank() && password.length >= 6 &&
                    (!needsSms || smsCode.length == 6) && s.passwordPhase != RailwayPasswordUiPhase.SLIDE_REQUIRED,
                    Modifier.testTag("railway-password-submit"))
            }
            if (s.accountBusy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("正在核验登录并获取乘车人…", color = Primary)
            }
            s.accountError?.let { WaitlistNotice(it) }
            SecondaryButton("打开 12306 App", onLogin, Modifier.testTag("railway-login"), enabled = !s.accountBusy)
            s.railwayAppNotice?.let { WaitlistNotice(it) }
            if (s.account != null) {
                SecondaryButton("刷新已授权乘车人", vm::refreshAccount, enabled = !s.accountBusy)
                SecondaryButton("切换账号", vm::resetPasswordForm, enabled = !s.accountBusy)
            } else SecondaryButton("乘车人（登录后读取）", {}, enabled = false,
                modifier = Modifier.testTag("waitlist-auth-disabled"))
            s.account?.let { account ->
                Text("请选择乘车人 · 已选 ${s.passengerSelection.size} 位", style = MaterialTheme.typography.titleMedium)
                if (account.passengers.isEmpty()) WaitlistNotice("当前账号没有可读取的乘车人，请先在 12306 添加并核验")
                account.passengers.forEachIndexed { index, p ->
                    ContentCard(Modifier.fillMaxWidth().testTag("railway-passenger-$index"),
                        selected = p.reference in s.passengerSelection) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .then(if (p.selectable) Modifier.clickable { vm.togglePassenger(p.reference) } else Modifier),
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(p.reference in s.passengerSelection,
                                { vm.togglePassenger(p.reference) }, enabled = p.selectable)
                            Column(Modifier.weight(1f)) {
                                Text("${p.displayName} · ${p.ticketLabel}", style = MaterialTheme.typography.titleMedium)
                                Text(p.maskedId, color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text(p.status, style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }
                Text("当前仅接入服务返回的可购成人票；儿童、学生等票种保留展示，不猜测其资格规则。",
                    color = Muted, style = MaterialTheme.typography.bodySmall)
                PrimaryButton("核对全部需求", vm::reviewOrder,
                    !s.accountBusy && s.selected.isNotEmpty() && s.passengerSelection.isNotEmpty())
            }
            if (s.selected.isEmpty()) Text("登录核验后请回候补查询选择需求，再进入确认页。", color = Muted)
        }
    }
}

@Composable internal fun RailwayConfirmationScreen(s: WaitlistUiState, vm: WaitlistViewModel) {
    SecureRailwayScreen()
    Column(Modifier.fillMaxSize()) {
        AppTopBar("确认候补需求", vm::back, backTag = "waitlist-back")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("${s.selected.size} 组需求 · ${s.passengerSelection.size} 位实际乘车人",
                style = MaterialTheme.typography.titleLarge)
            s.selected.forEach { choice -> ContentCard(Modifier.fillMaxWidth()) {
                Text("${choice.trip.date} · ${choice.trip.trainCode} · ${choice.seat.label}")
                Text("${choice.trip.from.name} → ${choice.trip.to.name}", color = Muted)
            } }
            WaitlistNotice("下一步需从当前会话重新核验所有车次、组合限制和兑现截止时间。当前页面不会发送订单。")
            PrimaryButton("确认并提交（接口核验中）", {}, enabled = false)
            Text("正式提交后每轮包含全部剩余需求；只排除能准确归因的拒绝项。未知结果先核对订单，不盲目重发。",
                color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

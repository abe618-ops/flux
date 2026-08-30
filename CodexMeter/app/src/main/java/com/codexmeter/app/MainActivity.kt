package com.codexmeter.app

import android.app.Application
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF080B10)
private val Panel = Color(0xFF10161F)
private val Panel2 = Color(0xFF151D28)
private val Line = Color(0xFF26313D)
private val Ink = Color(0xFFEAF2F7)
private val Muted = Color(0xFF8B9AA8)
private val Mint = Color(0xFF38E8A5)
private val Cyan = Color(0xFF5CD7FF)
private val Amber = Color(0xFFFFC857)
private val Danger = Color(0xFFFF6B6B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        RefreshWorker.schedule(this)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Mint, secondary = Cyan, background = Bg,
                    surface = Panel, onBackground = Ink, onSurface = Ink
                )
            ) { CodexMeterApp() }
        }
    }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val storage = Storage(app)
    private val bridgeClient = BridgeClient(app)
    private val phoneRuntime = PhoneRuntimeManager(app)
    private val phoneClient = PhoneCodexRpcClient(phoneRuntime)

    private val _usage = MutableStateFlow(storage.latest() ?: UsageSnapshot.demo())
    val usage: StateFlow<UsageSnapshot> = _usage
    private val _status = MutableStateFlow(if (storage.latest() == null) "正在初始化手机 Codex…" else "已载入最近快照 · 正在刷新")
    val status: StateFlow<String> = _status
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading
    private val _deviceLogin = MutableStateFlow<DeviceCodeLogin?>(null)
    val deviceLogin: StateFlow<DeviceCodeLogin?> = _deviceLogin
    private val _phoneDetail = MutableStateFlow("检查本机 runtime…")
    val phoneDetail: StateFlow<String> = _phoneDetail

    init { refresh() }

    fun refresh() {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            _status.value = "正在读取手机 Codex…"
            val phone = withContext(Dispatchers.IO) {
                val rt = phoneRuntime.ensureReady()
                _phoneDetail.value = rt.detail
                if (rt.ready) phoneClient.readUsage() else Result.failure(IllegalStateException(rt.detail))
            }
            if (phone.isSuccess) {
                val snapshot = phone.getOrThrow()
                _usage.value = snapshot
                storage.save(snapshot)
                _status.value = "PHONE · 本机 Codex"
                _phoneDetail.value = "手机独立模式已连接"
            } else {
                val phoneError = phone.exceptionOrNull()?.message ?: "手机 Codex 不可用"
                _phoneDetail.value = phoneError
                _status.value = "手机模式不可用，尝试 Bridge…"
                val bridge = withContext(Dispatchers.IO) { bridgeClient.fetch() }
                bridge.onSuccess {
                    _usage.value = it
                    _status.value = "BRIDGE · " + (storage.bridgeName.ifBlank { it.source })
                }.onFailure {
                    _status.value = "需要手机登录 · " + phoneError
                }
            }
            _loading.value = false
            UsageWidgetProvider.updateAll(getApplication())
        }
    }

    fun startPhoneLogin() {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            _status.value = "正在启动 ChatGPT 设备码登录…"
            val result = withContext(Dispatchers.IO) { phoneClient.startDeviceCodeLogin() }
            result.onSuccess {
                _deviceLogin.value = it
                _status.value = "请在浏览器完成 ChatGPT 授权"
                _phoneDetail.value = "设备码 " + it.userCode
            }.onFailure {
                _status.value = "手机登录启动失败 · " + (it.message ?: "unknown")
                _phoneDetail.value = it.message ?: "登录启动失败"
            }
            _loading.value = false
        }
    }

    fun checkPhoneLogin() {
        viewModelScope.launch {
            _status.value = "正在确认手机登录…"
            val ok = withContext(Dispatchers.IO) { phoneClient.accountReady() }
            if (ok) {
                _deviceLogin.value = null
                refresh()
            } else {
                _status.value = "尚未完成授权"
            }
        }
    }

    fun bridgeUrl() = storage.bridgeUrl
    fun bridgeName() = storage.bridgeName
    fun rediscover() {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            val result = withContext(Dispatchers.IO) { bridgeClient.rediscover() }
            result.onSuccess {
                _usage.value = it
                storage.save(it)
                _status.value = "BRIDGE · " + (storage.bridgeName.ifBlank { it.source })
            }.onFailure {
                _status.value = "未发现 Bridge · " + (it.message ?: "unknown")
            }
            _loading.value = false
        }
    }
    fun efficiency() = storage.usageEfficiency()
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("总览", Icons.Rounded.Dashboard),
    Tokens("Token", Icons.Rounded.DataUsage),
    Tasks("任务", Icons.Rounded.ListAlt),
    Trends("趋势", Icons.Rounded.Insights),
    Settings("设置", Icons.Rounded.Settings)
}

@Composable
private fun CodexMeterApp(vm: MainViewModel = viewModel()) {
    val s by vm.usage.collectAsState()
    val status by vm.status.collectAsState()
    val loading by vm.loading.collectAsState()
    var tab by remember { mutableStateOf(Tab.Home) }

    Scaffold(
        containerColor = Bg,
        topBar = { Header(s, status, loading, vm::refresh) },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0B1017), tonalElevation = 0.dp) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = item == tab,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, item.label, modifier = Modifier.size(21.dp)) },
                        label = { Text(item.label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Mint, selectedTextColor = Mint,
                            indicatorColor = Panel2, unselectedIconColor = Muted, unselectedTextColor = Muted
                        )
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(Bg)) {
            when (tab) {
                Tab.Home -> HomeScreen(s, vm.efficiency())
                Tab.Tokens -> TokenScreen(s)
                Tab.Tasks -> TaskScreen(s)
                Tab.Trends -> TrendScreen(s, vm.efficiency())
                Tab.Settings -> SettingsScreen(vm)
            }
        }
    }
}

@Composable
private fun Header(s: UsageSnapshot, status: String, loading: Boolean, refresh: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Bg).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Mint),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.Terminal, null, tint = Color(0xFF05231A)) }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("CodexMeter", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Ink)
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = if (!s.hasLiveQuota) Color(0xFF382F18) else Color(0xFF123628)
                ) {
                    Text(
                        if (!s.hasLiveQuota) "NO DATA" else "LIVE",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        color = if (!s.hasLiveQuota) Amber else Mint,
                        fontWeight = FontWeight.Bold, fontSize = 9.sp
                    )
                }
            }
            Text(status, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = refresh, enabled = !loading) {
            Icon(Icons.Rounded.Refresh, "Refresh", tint = if (loading) Muted else Ink)
        }
    }
}

@Composable
private fun HomeScreen(s: UsageSnapshot, efficiency: Pair<Double, Int>?) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CardBlock {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ring(s.healthScore)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("CODEX HEALTH", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(
                        when {
                            s.healthScore < 0 -> "No live data"
                            s.healthScore >= 85 -> "Plenty"
                            s.healthScore >= 70 -> "Good"
                            s.healthScore >= 50 -> "Moderate"
                            s.healthScore >= 30 -> "Tight"
                            else -> "Critical"
                        },
                        color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold
                    )
                    Text("综合短窗口与周窗口压力", color = Muted, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
            Quota("5 小时", s.fiveHourUsed, resetText(s.fiveHourResetAt), Mint)
            Spacer(Modifier.height(14.dp))
            Quota("每周", s.weeklyUsed, resetText(s.weeklyResetAt), Cyan)
        }

        CardBlock {
            SectionTitle("TOKEN ACTIVITY", Icons.Rounded.Memory)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                Metric("今日", formatTokens(s.tokensToday), Modifier.weight(1f))
                Metric("本周", formatTokens(s.tokensWeek), Modifier.weight(1f))
                Metric("30 天", formatTokens(s.tokens30d), Modifier.weight(1f))
                Metric("累计", formatTokens(s.lifetimeTokens), Modifier.weight(1f))
            }
            Spacer(Modifier.height(18.dp))
            val total = s.inputTokens + s.cachedInputTokens + s.outputTokens
            Breakdown("Input", s.inputTokens, total, Cyan)
            Spacer(Modifier.height(9.dp))
            Breakdown("Cached", s.cachedInputTokens, total, Mint)
            Spacer(Modifier.height(9.dp))
            Breakdown("Output", s.outputTokens, total, Amber)
            Spacer(Modifier.height(10.dp))
            Text("Cache hit " + (s.cacheRate * 100).toInt() + "%", color = Muted, fontSize = 10.sp)
        }

        CardBlock {
            SectionTitle("14-DAY VELOCITY", Icons.Rounded.ShowChart)
            Spacer(Modifier.height(14.dp))
            Sparkline(s.daily.takeLast(14).map { it.tokens.toFloat() }, Modifier.fillMaxWidth().height(120.dp))
        }

        CardBlock {
            SectionTitle("USAGE INTELLIGENCE", Icons.Rounded.AutoGraph)
            Spacer(Modifier.height(12.dp))
            Insight(Icons.Rounded.Percent, "UC ↔ Token", efficiency?.let { "1% ≈ " + formatTokens(it.first.toLong()) } ?: "正在积累真实快照", Cyan)
            Insight(Icons.Rounded.Speed, "周剩余额度", if (s.hasLiveQuota) s.weeklyRemaining.toInt().toString() + "% remaining" else "等待真实数据", if (s.weeklyUsed >= 80) Danger else Mint)
            Insight(Icons.Rounded.Schedule, "最近重置", resetText(s.fiveHourResetAt), Mint)
        }

        CardBlock {
            SectionTitle("ACCOUNT", Icons.Rounded.AccountCircle)
            Spacer(Modifier.height(10.dp))
            Labeled("Plan", s.plan)
            Labeled("Account", s.account)
            Labeled("Source", s.source)
            Labeled("Reset credits", s.resetCredits.toString())
            s.creditsBalance?.let { Labeled("Credits", "%.2f".format(it)) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TokenScreen(s: UsageSnapshot) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PageTitle("Token 分析", "账户级 Token 活动与每日桶")
        CardBlock {
            Row {
                BigMetric("Lifetime", formatTokens(s.lifetimeTokens), Mint, Modifier.weight(1f))
                BigMetric("30 days", formatTokens(s.tokens30d), Cyan, Modifier.weight(1f))
            }
        }
        CardBlock {
            SectionTitle("TOKEN MIX", Icons.Rounded.DonutLarge)
            Spacer(Modifier.height(14.dp))
            val total = s.inputTokens + s.cachedInputTokens + s.outputTokens
            Breakdown("Input", s.inputTokens, total, Cyan)
            Spacer(Modifier.height(12.dp))
            Breakdown("Cached", s.cachedInputTokens, total, Mint)
            Spacer(Modifier.height(12.dp))
            Breakdown("Output", s.outputTokens, total, Amber)
        }
        CardBlock {
            SectionTitle("DAILY BUCKETS", Icons.Rounded.CalendarMonth)
            Spacer(Modifier.height(8.dp))
            if (s.daily.isEmpty()) Text("等待 account/usage/read 的每日数据", color = Muted, fontSize = 11.sp)
            s.daily.takeLast(14).reversed().forEach {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(it.date, color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Text(formatTokens(it.tokens), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun TaskScreen(s: UsageSnapshot) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PageTitle("任务排行", "找出最消耗额度的 Codex threads")
        if (s.tasks.isEmpty()) {
            CardBlock { Text("当前 Bridge 尚未返回 thread usage；V1.1 可启用 recent-thread 扫描。", color = Muted, lineHeight = 18.sp) }
        } else {
            val max = (s.tasks.maxOfOrNull { it.tokens } ?: 1L).toFloat()
            s.tasks.sortedByDescending { it.tokens }.forEachIndexed { index, t ->
                CardBlock {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(if (index == 0) Mint.copy(alpha = .14f) else Panel2),
                            contentAlignment = Alignment.Center
                        ) { Text((index + 1).toString(), color = if (index == 0) Mint else Muted, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.title, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.model, color = Muted, fontSize = 10.sp)
                        }
                        Text(formatTokens(t.tokens), color = Mint, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { (t.tokens.toFloat() / max).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                        color = Mint, trackColor = Color(0xFF202A34)
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendScreen(s: UsageSnapshot, efficiency: Pair<Double, Int>?) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PageTitle("趋势与效率", "把额度百分比和 Token 活动放到同一时间轴")
        CardBlock {
            SectionTitle("TOKEN VELOCITY", Icons.Rounded.Timeline)
            Spacer(Modifier.height(14.dp))
            Sparkline(s.daily.map { it.tokens.toFloat() }, Modifier.fillMaxWidth().height(170.dp))
        }
        CardBlock {
            SectionTitle("UC EFFICIENCY", Icons.Rounded.Percent)
            Spacer(Modifier.height(12.dp))
            Text(efficiency?.let { formatTokens(it.first.toLong()) + " Token" } ?: "Collecting…", color = Mint, fontSize = 31.sp, fontWeight = FontWeight.Bold)
            Text("估算每消耗 1% Weekly quota 对应的 Token", color = Muted, fontSize = 11.sp)
            Spacer(Modifier.height(12.dp))
            Text("这是本机连续快照估算，不是 OpenAI 固定兑换率；模型、reasoning 与工具调用都会改变额度效率。", color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
        }
        CardBlock {
            SectionTitle("RESET WINDOWS", Icons.Rounded.Schedule)
            Spacer(Modifier.height(12.dp))
            Quota("5 小时", s.fiveHourUsed, resetText(s.fiveHourResetAt), Mint)
            Spacer(Modifier.height(16.dp))
            Quota("每周", s.weeklyUsed, resetText(s.weeklyResetAt), Cyan)
        }
    }
}

@Composable
private fun SettingsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val login by vm.deviceLogin.collectAsState()
    val phoneDetail by vm.phoneDetail.collectAsState()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PageTitle("手机独立模式", "不需要电脑、不需要 IP、不需要 Bridge")
        CardBlock {
            SectionTitle("PHONE CODEX", Icons.Rounded.Smartphone)
            Spacer(Modifier.height(12.dp))
            Text(phoneDetail, color = if (login == null) Muted else Mint, fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(14.dp))
            if (login == null) {
                Button(
                    onClick = vm::startPhoneLogin,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Color(0xFF05231A))
                ) {
                    Icon(Icons.Rounded.Login, null)
                    Spacer(Modifier.width(8.dp))
                    Text("使用 ChatGPT 登录手机 Codex", fontWeight = FontWeight.Bold)
                }
            } else {
                Surface(shape = RoundedCornerShape(16.dp), color = Panel2) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text("一次性设备码", color = Muted, fontSize = 10.sp)
                        Text(login!!.userCode, color = Mint, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(login!!.verificationUrl)))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.OpenInBrowser, null)
                    Spacer(Modifier.width(8.dp))
                    Text("打开 ChatGPT 授权页面")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = vm::checkPhoneLogin, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.CheckCircle, null)
                    Spacer(Modifier.width(8.dp))
                    Text("我已完成授权")
                }
            }
        }
        CardBlock {
            SectionTitle("运行方式", Icons.Rounded.Memory)
            Spacer(Modifier.height(10.dp))
            Text(
                "Codex ARM64 runtime 被打包进 APK。首次启动解压到应用私有目录，随后在手机本机启动 codex app-server；额度数据通过官方 account/read、account/rateLimits/read、account/usage/read 获取。",
                color = Muted, fontSize = 11.sp, lineHeight = 18.sp
            )
        }
        CardBlock {
            SectionTitle("电脑 Bridge · 备用", Icons.Rounded.Lan)
            Spacer(Modifier.height(10.dp))
            Text(
                if (vm.bridgeUrl().isBlank()) "只有手机 runtime 无法运行时才需要电脑 Bridge。"
                else "已保存备用 Bridge：" + vm.bridgeName().ifBlank { vm.bridgeUrl() },
                color = Muted, fontSize = 11.sp
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = vm::rediscover, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Radar, null)
                Spacer(Modifier.width(8.dp))
                Text("重新寻找备用 Bridge")
            }
        }
        CardBlock {
            SectionTitle("PRIVACY", Icons.Rounded.Security)
            Spacer(Modifier.height(10.dp))
            Text(
                "手机独立模式使用自己的应用私有 CODEX_HOME。它不会读取 ChatGPT App 的沙箱数据，也不会把 auth.json 发送给电脑 Bridge。卸载 App 会同时删除这份手机端登录状态。",
                color = Muted, fontSize = 11.sp, lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun CardBlock(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Panel).padding(17.dp),
        content = content
    )
}

@Composable
private fun SectionTitle(text: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Mint, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
private fun PageTitle(title: String, sub: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(title, color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(sub, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun Ring(score: Int) {
    Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 10.dp.toPx()
            val p = sw / 2
            drawArc(Color(0xFF202B35), -90f, 360f, false, Offset(p, p), androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw), style = Stroke(sw, cap = StrokeCap.Round))
            if (score >= 0) drawArc(if (score < 35) Danger else Mint, -90f, 360f * score / 100f, false, Offset(p, p), androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw), style = Stroke(sw, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (score < 0) "--" else score.toString(), color = Ink, fontSize = 31.sp, fontWeight = FontWeight.Bold)
            Text(if (score < 0) "NO DATA" else "/100", color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun Quota(label: String, used: Float, reset: String, color: Color) {
    val remaining = if (used < 0f) -1f else (100f - used).coerceIn(0f, 100f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(72.dp))
        Text(if (remaining < 0f) "--" else remaining.toInt().toString() + "%", color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.weight(1f))
        Text(if (remaining < 0f) "未连接真实数据" else "剩余 · " + reset + " 后重置", color = Muted, fontSize = 10.sp)
    }
    Spacer(Modifier.height(7.dp))
    LinearProgressIndicator(progress = { if (remaining < 0f) 0f else (remaining / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape), color = color, trackColor = Color(0xFF202A34))
    if (used >= 0f) {
        Spacer(Modifier.height(4.dp))
        Text("已用 " + used.toInt() + "% · 剩余 " + remaining.toInt() + "%", color = Muted, fontSize = 9.sp)
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = Muted, fontSize = 10.sp)
        Text(value, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BigMetric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label.uppercase(), color = Muted, fontSize = 10.sp)
        Text(value, color = color, fontSize = 29.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Breakdown(label: String, value: Long, total: Long, color: Color) {
    val p = if (total <= 0L) 0f else value.toFloat() / total.toFloat()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, modifier = Modifier.width(62.dp), fontSize = 11.sp)
        Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(Color(0xFF202A34))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(p.coerceIn(0f, 1f)).background(color))
        }
        Spacer(Modifier.width(10.dp))
        Text(formatTokens(value), color = Ink, fontSize = 11.sp, modifier = Modifier.width(56.dp))
    }
}

@Composable
private fun Insight(icon: ImageVector, label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(label, color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(value, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Labeled(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(k, color = Muted, fontSize = 11.sp, modifier = Modifier.width(96.dp))
        Text(v, color = Ink, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Sparkline(values: List<Float>, modifier: Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val max = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val min = values.minOrNull() ?: 0f
        val range = (max - min).coerceAtLeast(1f)
        repeat(4) { i ->
            val y = size.height * i / 3f
            drawLine(Line.copy(alpha = .65f), Offset(0f, y), Offset(size.width, y), 1f)
        }
        val pts = values.mapIndexed { i, v ->
            Offset(size.width * i / (values.size - 1), size.height - (v - min) / range * size.height * .82f - size.height * .08f)
        }
        for (i in 0 until pts.size - 1) drawLine(Mint, pts[i], pts[i + 1], 3.dp.toPx(), cap = StrokeCap.Round)
        pts.forEach { drawCircle(Mint, 2.4.dp.toPx(), it) }
    }
}

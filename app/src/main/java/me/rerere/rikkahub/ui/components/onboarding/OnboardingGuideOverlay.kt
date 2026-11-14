package me.rerere.rikkahub.ui.components.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.BubbleChat
import me.rerere.hugeicons.stroke.Database02
import me.rerere.hugeicons.stroke.Github
import me.rerere.hugeicons.stroke.Idea01
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.ui.toast.ToastType

private val ObsidianBackdrop = Color(0xFF0B0C12)
private val SingularityGold = Color(0xFFF3CE84)

/**
 * 冷启动高定矢量开屏过渡遮罩：与系统 SplashScreen 底色一致，
 * 用纯矢量光环 + 渐显字标平滑承接至主界面或新手引导。
 */
@Composable
fun KairosSplashOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        revealed = true
    }

    val logoScale by animateFloatAsState(
        targetValue = if (revealed) 1f else 0.88f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "splash_logo_scale",
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0.25f,
        animationSpec = tween(durationMillis = 560, easing = FastOutSlowInEasing),
        label = "splash_glow_alpha",
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(360)) + scaleOut(targetScale = 1.03f, animationSpec = tween(360)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBackdrop),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .size(280.dp)
                    .graphicsLayer { alpha = glowAlpha }
            ) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SingularityGold.copy(alpha = 0.20f),
                            SingularityGold.copy(alpha = 0.05f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.minDimension * 0.5f,
                    )
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_splash_kairos),
                    contentDescription = "Kairos",
                    modifier = Modifier
                        .size(164.dp)
                        .graphicsLayer {
                            scaleX = logoScale
                            scaleY = logoScale
                        },
                )

                AnimatedVisibility(
                    visible = revealed,
                    enter = fadeIn(tween(420, delayMillis = 120)) +
                        slideInVertically(tween(420, delayMillis = 120)) { it / 3 },
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "K A I R O S",
                            color = Color(0xFFF6F1E6),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 6.sp,
                        )
                        Text(
                            text = "恰逢其时的记忆 · 只需 3 个模型",
                            color = Color(0xFFA7ACB9),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

private data class OnboardingSlide(
    val tag: String,
    val title: String,
    val subtitle: String,
    val description: String,
)

private val OnboardingSlides = listOf(
    OnboardingSlide(
        tag = "01 / 06 · 第一阶：零门槛先聊",
        title = "第一阶：零门槛先聊\n1 把 Key 就能直接开演",
        subtitle = "不限平台 · 先把天聊起来",
        description = "刚装好 App 别被高阶参数吓到！先选任意一家你能拿到 Key 的平台（DeepSeek、硅基流动、OpenRouter、Google 等），在「设置 → 提供商」填入 1 把 LLM Key 保存，再去「模型设置 → 主对话模型」选好聊天模型，就能立刻开聊。",
    ),
    OnboardingSlide(
        tag = "02 / 06 · 第二阶：核心模型 01",
        title = "主对话模型 (Chat)\n撑起逻辑与文风上限",
        subtitle = "体验 = 逻辑底座 × 语言颗粒度",
        description = "会聊天之后，想让长线 RP 不失忆、不塞满万字死设定，需要配齐 3 个各司其职的模型。第 1 个是主对话模型：把最聪明的旗舰大脑完全留给正文演出、角色口吻与剧情张力，不被检索杂活分散算力。",
    ),
    OnboardingSlide(
        tag = "03 / 06 · 第二阶：核心模型 02",
        title = "向量嵌入模型 (Embedding)\n负责一阶段广域海选",
        subtitle = "长期设定档案 + 早期历史切片",
        description = "在云端把冷层设定（/topics 等）与溢出窗口的早年对话编码并持久化本地 VEC1 缓存。每一轮先毫秒级算出语义余弦相似度，捞出一阶段候选记忆池（未配时也自动降级词法召回）。",
    ),
    OnboardingSlide(
        tag = "04 / 06 · 第二阶：核心模型 03",
        title = "决策重排模型 (Decision)\n二阶段裁决 + 缓存护盾",
        subtitle = "推荐 gemini-decision-1 · 死保前缀缓存",
        description = "结合当前语境对一阶段候选池精准打分，滤除伪相关噪音后只将最切题的几段从尾部注入——头部 System Prompt 与 /core.md、/state.md 字节级不变，100% 死保云端 Prompt Caching 不被击穿。",
    ),
    OnboardingSlide(
        tag = "05 / 06 · 第三阶：终极谷歌路线 A",
        title = "AIS 自建多项目免费号池\n零成本满配三模型",
        subtitle = "单 Key 自动吃强轮弱 · 前 2 把锁嵌入与决策",
        description = "Google AI Studio 每个独立项目每天免费给 4 Hydrogen (20) + 3.8 Flash (20) + 3.6 Flash (20) 共 60 次主力对话，外加 Embedding 2 (1,000 次/日) 和 Decision 1 (10,000 次/日)！务必点「创建新项目」每项目拿 1 把 Key（同项目多建 Key 额度共享无效），批量粘贴进 Google 提供商即可：单 Key 内按 Hydrogen → 3.8 → 3.6 自动吃强轮弱（1 分钟全冷却回 #1/#2 的 3.5fl 兜底，前 3 个 RPD 日满立刻切下一把 Key 不等 500；3.5fl / Embedding / Decision 自动锁定前 2 把 Key）。",
    ),
    OnboardingSlide(
        tag = "06 / 06 · 第三阶：终极谷歌路线 B",
        title = "官方 Gemini App + NotebookLM\n零折腾原生绿色 RP",
        subtitle = "Source 知识库静态解耦 · 1~2 句定调即开演",
        description = "连 API 号池都懒得配？直接用纯谷歌官方免费全家桶：把原作小说、人物设定集与世界观丢进 Google NotebookLM 提炼角色关系网、场景锚点与大纲；再在官方 Gemini App（gemini.google.com 或客户端 / Gem）里直接挂载该 NotebookLM 笔记本作为 Source，开聊仅需 1~2 句定调，0 反代、100% 原生绿色畅玩。",
    ),
)

/**
 * 首次启动「PPT 式点击切换动画」新手指引浮层。
 * 点击中间演示卡片或底部按钮可逐页推进动画，支持左右滑动与随时跳过。
 */
@Composable
fun OnboardingGuideOverlay(
    onFinish: () -> Unit,
    onGoToProviders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { OnboardingSlides.size })
    val scope = rememberCoroutineScope()
    val currentPage = pagerState.currentPage
    val isLastPage = currentPage == OnboardingSlides.lastIndex

    // 每切换一页，驱动该页内部的阶梯式 PPT 元素入场动画
    var activeBeat by remember { mutableIntStateOf(0) }
    LaunchedEffect(currentPage) {
        activeBeat = 0
        delay(120)
        activeBeat = 1
        delay(180)
        activeBeat = 2
        delay(180)
        activeBeat = 3
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // 顶部品牌标 + 步进胶囊指示器 + 跳过按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_splash_kairos),
                        contentDescription = null,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape),
                    )
                    Text(
                        text = "Kairos 指南",
                        style = MaterialTheme.typography.titleSmallEmphasized,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(OnboardingSlides.size) { index ->
                        val selected = index == currentPage
                        val width by animateDpAsState(
                            targetValue = if (selected) 24.dp else 7.dp,
                            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                            label = "dot_width_$index",
                        )
                        val dotColor by animateColorAsState(
                            targetValue = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                            label = "dot_color_$index",
                        )
                        Box(
                            modifier = Modifier
                                .height(7.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(dotColor)
                                .clickable {
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                }
                        )
                    }
                }

                TextButton(onClick = onFinish) {
                    Text(if (isLastPage) "关闭" else "跳过")
                }
            }

            // 中央 PPT 幻灯片舞台（支持左右滑动 + 点击卡片直接切换下一页）
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                val slide = OnboardingSlides[page]
                val isCurrent = page == currentPage
                val stageBeat = if (isCurrent) activeBeat else 3

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
                ) {
                    // 动态可点击演示舞台卡片（点击直接推进到下一张 PPT）
                    Surface(
                        onClick = {
                            if (!isLastPage) {
                                scope.launch { pagerState.animateScrollToPage(currentPage + 1) }
                            } else {
                                onGoToProviders()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ) {
                                    Text(
                                        text = slide.tag,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                                if (!isLastPage) {
                                    Text(
                                        text = "轻触画面切换 →",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            AnimatedContent(
                                targetState = page,
                                transitionSpec = {
                                    (fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 8 } + scaleIn(
                                        initialScale = 0.96f,
                                        animationSpec = tween(260),
                                    )) togetherWith (fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { -it / 8 })
                                },
                                label = "ppt_stage_content",
                            ) { animPage ->
                                PptDiagramStage(
                                    page = animPage,
                                    beat = stageBeat,
                                )
                            }
                        }
                    }

                    // 标题与精炼说明区
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = slide.title,
                            style = MaterialTheme.typography.headlineSmallEmphasized,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = slide.subtitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = slide.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 22.sp,
                        )
                    }
                }
            }

            // 底部控制栏
            if (isLastPage) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = onGoToProviders,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = CircleShape,
                    ) {
                        Icon(
                            imageVector = HugeIcons.Sparkles,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("去添加提供商并配置 3 个模型")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilledTonalButton(
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(currentPage - 1) }
                            },
                            modifier = Modifier.weight(1f),
                            shape = CircleShape,
                        ) {
                            Icon(HugeIcons.ArrowLeft01, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("上一页")
                        }
                        TextButton(
                            onClick = onFinish,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("先逛逛聊天页")
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage((currentPage - 1).coerceAtLeast(0)) }
                        },
                        enabled = currentPage > 0,
                        shape = CircleShape,
                    ) {
                        Icon(HugeIcons.ArrowLeft01, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("上一页")
                    }

                    Button(
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(currentPage + 1) }
                        },
                        shape = CircleShape,
                    ) {
                        Text("下一步")
                        Spacer(Modifier.width(6.dp))
                        Icon(HugeIcons.ArrowRight01, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PptDiagramStage(
    page: Int,
    beat: Int,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (page) {
            0 -> {
                PptRoleRow(
                    index = "01",
                    icon = HugeIcons.Idea01,
                    title = "第一阶 · 1 把 Key 零门槛先聊",
                    detail = "任意平台填入 LLM Key 并选主对话模型即可开聊",
                    highlighted = beat >= 1,
                    accent = MaterialTheme.colorScheme.primary,
                )
                PptRoleRow(
                    index = "02",
                    icon = HugeIcons.Database02,
                    title = "第二阶 · 三模型分工长线记忆",
                    detail = "Chat + Embedding + Decision，不失忆且保缓存",
                    highlighted = beat >= 2,
                    accent = MaterialTheme.colorScheme.secondary,
                )
                PptRoleRow(
                    index = "03",
                    icon = HugeIcons.Sparkles,
                    title = "第三阶 · 全谷歌免费终极双路线",
                    detail = "AIS 多项目免费号池 / Gemini App + NotebookLM",
                    highlighted = beat >= 3,
                    accent = MaterialTheme.colorScheme.tertiary,
                )
            }

            1 -> {
                PptRoleRow(
                    index = "01",
                    icon = HugeIcons.BubbleChat,
                    title = "主对话模型 · 灵魂与逻辑核心",
                    detail = "Claude / Gemini / DeepSeek 等主力大模型",
                    highlighted = true,
                    accent = MaterialTheme.colorScheme.primary,
                )
                AnimatedVisibility(
                    visible = beat >= 1,
                    enter = fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 4 },
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = HugeIcons.Idea01,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "把高智商窗口完全留给叙事演绎、角色扮演与 MCP/工作区工具调用。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PptMiniPill(text = "02 Embedding · 一阶段粗召回", modifier = Modifier.weight(1f), active = false)
                    PptMiniPill(text = "03 Decision · 二阶段精排", modifier = Modifier.weight(1f), active = false)
                }
            }

            2 -> {
                PptRoleRow(
                    index = "02",
                    icon = HugeIcons.Database02,
                    title = "向量嵌入模型 · Stage 1 海选",
                    detail = "topics 档案 + 溢出窗口早年历史 → VEC1 缓存",
                    highlighted = true,
                    accent = MaterialTheme.colorScheme.secondary,
                )
                AnimatedVisibility(
                    visible = beat >= 1,
                    enter = fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 4 },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PptMiniPill(text = "记忆档案切片", modifier = Modifier.weight(1f), active = beat >= 1)
                        PptMiniPill(text = "早期对话兜底", modifier = Modifier.weight(1f), active = beat >= 2)
                        PptMiniPill(text = "本地毫秒召回", modifier = Modifier.weight(1f), active = beat >= 3)
                    }
                }
                Text(
                    text = "无嵌入模型时会自动走本地词法相关性兜底，配好云端嵌入后召回质量质变。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            3 -> {
                PptRoleRow(
                    index = "03",
                    icon = HugeIcons.Sparkles,
                    title = "决策重排模型 · Stage 2 裁决 + 缓存护盾",
                    detail = "推荐 gemini-decision-1（单前向打分）或轻量模型",
                    highlighted = true,
                    accent = MaterialTheme.colorScheme.tertiary,
                )
                AnimatedVisibility(
                    visible = beat >= 1,
                    enter = fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 4 },
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PptMiniPill(
                                text = "1. 头部设定 & core.md 🔒 字节级不变 · 前缀缓存 100% 命中",
                                modifier = Modifier.fillMaxWidth(),
                                active = beat >= 1,
                            )
                            PptMiniPill(
                                text = "2. Decision 剔除伪相关记忆 → 仅将 Top-6 从尾部注入 ✨",
                                modifier = Modifier.fillMaxWidth(),
                                active = beat >= 2,
                            )
                            PptMiniPill(
                                text = "3. 你的最新输入 💬 逻辑不断档 · 长叙事极其省钱",
                                modifier = Modifier.fillMaxWidth(),
                                active = beat >= 3,
                            )
                        }
                    }
                }
            }

            4 -> {
                PptRoleRow(
                    index = "A",
                    icon = HugeIcons.Sparkles,
                    title = "Google AI Studio · 多项目免费号池",
                    detail = "全谷歌生态零成本配齐三模型 API",
                    highlighted = true,
                    accent = MaterialTheme.colorScheme.primary,
                )
                AnimatedVisibility(
                    visible = beat >= 1,
                    enter = fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 4 },
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PptMiniPill(
                                text = "单独立项目每日：Hydrogen(20) + 3.8(20) + 3.6(20) + Emb2(1K) + Dec1(10K)",
                                modifier = Modifier.fillMaxWidth(),
                                active = beat >= 1,
                            )
                            PptMiniPill(
                                text = "避坑铁律：必须「创建新项目」每项目 1 把 Key，同项目多 Key 共享无效",
                                modifier = Modifier.fillMaxWidth(),
                                active = beat >= 2,
                            )
                            PptMiniPill(
                                text = "自动调度：Hydrogen→3.8→3.6 吃强轮弱 · 3.5fl/Emb/Dec 锁前 2 把",
                                modifier = Modifier.fillMaxWidth(),
                                active = beat >= 3,
                            )
                        }
                    }
                }
            }

            else -> {
                val uriHandler = LocalUriHandler.current
                val toaster = LocalToaster.current
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = HugeIcons.Idea01,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "终极路线 B · 原生绿色全家桶",
                                style = MaterialTheme.typography.titleSmallEmphasized,
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        PptCheckStep(number = "1", text = "NotebookLM 导入小说/世界观 → 提炼关系网、锚点与大纲", visible = beat >= 1)
                        PptCheckStep(number = "2", text = "Gemini App / Gem 直接挂载 NotebookLM 作为 Source", visible = beat >= 2)
                        PptCheckStep(number = "✓", text = "开聊仅需 1~2 句定调：0 反代、零维护、原生满血体验", visible = beat >= 3)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val links = listOf(
                        "AI Studio" to "https://aistudio.google.com/apikey",
                        "Gemini App" to "https://gemini.google.com/",
                        "NotebookLM" to "https://notebooklm.google.com/",
                    )
                    links.forEach { (label, url) ->
                        OutlinedButton(
                            onClick = {
                                runCatching { uriHandler.openUri(url) }
                                    .onFailure { toaster.show("无法打开浏览器: ${it.message}", type = ToastType.Error) }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PptRoleRow(
    index: String,
    icon: ImageVector,
    title: String,
    detail: String,
    highlighted: Boolean,
    accent: Color,
) {
    val containerColor by animateColorAsState(
        targetValue = if (highlighted) {
            accent.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "ppt_row_bg_$index",
    )
    val borderColor by animateColorAsState(
        targetValue = if (highlighted) accent.copy(alpha = 0.55f) else Color.Transparent,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "ppt_row_border_$index",
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        color = if (highlighted) accent else MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                    tint = if (highlighted) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = index,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (highlighted) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmallEmphasized,
                    )
                }
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PptMiniPill(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val bg by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "mini_pill_bg",
    )
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = bg,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            color = if (active) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun PptCheckStep(
    number: String,
    text: String,
    visible: Boolean,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(240)) + slideInHorizontally(tween(240)) { -it / 6 },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (number == "✓") {
                    Icon(
                        imageVector = HugeIcons.Tick01,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        text = number,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * 累计启动满 10 次后弹出一次的 GitHub Star 邀请框（只打扰一次，关闭或跳转即永久记住）。
 */
@Composable
fun GithubStarDialog(
    launchCount: Int,
    onStar: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = HugeIcons.Github,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
        },
        title = {
            Text(
                text = "第 $launchCount 次打开 Kairos 了",
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                text = "如果 Kairos 的现代化 RP 理念与三模型协作记忆系统合你胃口，欢迎去 GitHub 给仓库点一颗免费的 Star！",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(
                onClick = onStar,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text("去点个 Star")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("下次再说")
            }
        },
    )
}

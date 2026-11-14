package me.rerere.mediagen.model

private val EXPLICIT_IMAGE_PATTERNS = listOf(
    "-image",
    "_image",
    "/image",
    "image-",
    "image_",
    "t2i",
    "i2i",
    "text-to-image",
    "image-to-image",
    "text2image",
    "image2image",
    "seedream",
    "dall-e",
    "dalle",
    "gpt-image",
    "imagen",
    "flux",
    "ideogram",
    "recraft",
    "midjourney",
    "kolors",
    "sdxl",
    "stable-diffusion",
    "nano-banana",
    "cogview",
    "hidream",
)

private val VIDEO_PATTERNS = listOf(
    "sora",
    "veo",
    "seedance",
    "kling",
    "hailuo",
    "minimax-h",
    "video-01",
    "runway",
    "gen-3",
    "gen-4",
    "gen3",
    "gen4",
    "luma",
    "ray-2",
    "ray2",
    "pika",
    "vidu",
    "cogvideo",
    "hunyuan-video",
    "grok-video",
    "jimeng-video",
    "pixverse",
    "mochi",
    "skyreels",
    "step-video",
    "seaweed",
    "magi-1",
    "-video",
    "_video",
    "/video",
    "video-",
    "video_",
    "t2v",
    "i2v",
    "v2v",
    "kf2v",
    "r2v",
    "s2v",
    "text-to-video",
    "image-to-video",
    "text2video",
    "image2video",
    "txt2vid",
    "img2vid",
)

private val IMAGE_PATTERNS = EXPLICIT_IMAGE_PATTERNS + listOf(
    "wanx",
    "jimeng",
    "bagel",
    "auraflow",
    "kontext",
    "qwen-image",
    "gemini-",
)

/**
 * 根据模型 ID 启发式识别媒体类型，未命中视频特征时默认按图像处理。
 */
fun inferMediaKind(modelId: String): MediaKind {
    val id = modelId.trim().lowercase()
    if (id.isEmpty()) return MediaKind.IMAGE
    if (EXPLICIT_IMAGE_PATTERNS.any { it in id }) return MediaKind.IMAGE
    if (VIDEO_PATTERNS.any { it in id }) return MediaKind.VIDEO
    // 阿里万相视频模型（如 wan2.1-plus、wan2.6-t2v）在排除明确 image 后按视频识别
    if ("wan" in id && ("animate" in id || "turbo" in id || "plus" in id || "3.0" in id)) {
        return MediaKind.VIDEO
    }
    return MediaKind.IMAGE
}

/**
 * 判断 `/models` 返回的 ID 是否大概率属于媒体创作模型（文生图/图生图/文生视频/图生视频）。
 */
fun looksLikeMediaGenerationModel(modelId: String): Boolean {
    val id = modelId.trim().lowercase()
    if (id.isEmpty()) return false
    if (inferMediaKind(id) == MediaKind.VIDEO) return true
    if ("gemini-" in id && "image" !in id && "imagen" !in id) return false
    return IMAGE_PATTERNS.any { it in id } || "image" in id
}

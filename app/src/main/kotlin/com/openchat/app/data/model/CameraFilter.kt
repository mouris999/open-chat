package com.openchat.app.data.model

import androidx.compose.ui.graphics.Color

/**
 * Camera Filter Model - Instagram/Snapchat style filters
 * Uses shader-based real-time effects
 */
data class CameraFilter(
    val id: String,
    val name: String,
    val description: String,
    val thumbnailUrl: String? = null,
    val filterType: FilterType,
    val shaderCode: String, // GLSL shader code
    val parameters: Map<String, FilterParameter> = emptyMap(),
    val creatorId: String? = null,
    val creatorName: String? = null,
    val isOfficial: Boolean = false,
    val downloadCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val accentColor: Color = Color(0xFF00D2FF)
) {
    // Unique pack ID when shared as a pack
    val packId: String get() = "pack_$id"
}

/**
 * Filter parameter for user-adjustable values
 */
data class FilterParameter(
    val name: String,
    val type: ParameterType,
    val defaultValue: Float,
    val minValue: Float = 0f,
    val maxValue: Float = 1f,
    val step: Float = 0.01f
)

enum class ParameterType {
    SLIDER, COLOR, SWITCH, CHOICE
}

/**
 * Predefined filter types with built-in shaders
 */
enum class FilterType {
    // Instagram-style filters
    NORMAL,
    CLARENDON,  // High contrast, cool tint
    GINGHAM,    // Vintage, low contrast
    MOON,       // B&W, high contrast
    LARK,       // Bright, vibrant
    JUNO,       // Cool, pink tint
    
    // Snapchat-style creative filters
    DOG_EARS,           // Face distortion + ears
    FACE_SWAP,          // Swap two faces
    BEAUTIFY,           // Smooth skin, brighten eyes
    RAINBOW_VOMIT,      // Rainbow overlay
    BIG_EYES,           // Eye enlargement
    
    // Trending filters
    GLITCH,             // Digital glitch effect
    VHS,                // Retro VHS look
    RETRO,              // 80s style
    CYBERPUNK,          // Neon, futuristic
    ANIME,              // Anime-style transformation
    
    // AR Effects
    NEON_GLOW,
    PARTICLE_DANCE,
    FACE_TRACKING_MASK,
    THREED_GLASSES,
    HEART_EYES,
    
    // Custom shaders
    CUSTOM_VERTEX,
    CUSTOM_FRAGMENT
}

/**
 * Filter Pack for sharing multiple filters
 */
data class FilterPack(
    val id: String,
    val name: String,
    val description: String,
    val coverImageUrl: String? = null,
    val filters: List<CameraFilter>,
    val creatorId: String,
    val creatorName: String,
    val downloadCount: Int = 0,
    val rating: Float = 5.0f,
    val createdAt: Long = System.currentTimeMillis(),
    val isOfficial: Boolean = false,
    val tags: List<String> = emptyList()
)

/**
 * Built-in shader definitions for each filter type
 */
object FilterShaders {
    
    // Instagram-style Clarendon filter
    val CLARENDON_SHADER = """
        precision mediump float;
        varying vec2 vTextureCoord;
        uniform sampler2D sTexture;
        
        void main() {
            vec4 color = texture2D(sTexture, vTextureCoord);
            // High contrast
            color.rgb = (color.rgb - 0.5) * 1.2 + 0.5;
            // Cool tint
            color.r *= 0.9;
            color.b *= 1.1;
            // Slight fade
            color.rgb = color.rgb * 0.9 + 0.1;
            gl_FragColor = color;
        }
    """.trimIndent()
    
    // Vintage Gingham filter
    val GINGHAM_SHADER = """
        precision mediump float;
        varying vec2 vTextureCoord;
        uniform sampler2D sTexture;
        
        void main() {
            vec4 color = texture2D(sTexture, vTextureCoord);
            // Desaturate slightly
            float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
            color.rgb = mix(color.rgb, vec3(gray), 0.3);
            // Warm tint
            color.r *= 1.1;
            color.b *= 0.9;
            // Fade
            color.rgb = color.rgb * 0.85 + 0.15;
            gl_FragColor = color;
        }
    """.trimIndent()
    
    // Black & White Moon filter
    val MOON_SHADER = """
        precision mediump float;
        varying vec2 vTextureCoord;
        uniform sampler2D sTexture;
        
        void main() {
            vec4 color = texture2D(sTexture, vTextureCoord);
            // Convert to B&W with high contrast
            float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
            gray = (gray - 0.5) * 1.3 + 0.5;
            color.rgb = vec3(gray);
            gl_FragColor = color;
        }
    """.trimIndent()
    
    // Bright Lark filter
    val LARK_SHADER = """
        precision mediump float;
        varying vec2 vTextureCoord;
        uniform sampler2D sTexture;
        
        void main() {
            vec4 color = texture2D(sTexture, vTextureCoord);
            // Brighten
            color.rgb = color.rgb * 1.15;
            // Increase saturation
            float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
            color.rgb = mix(vec3(gray), color.rgb, 1.3);
            // Slight warm tint
            color.r *= 1.05;
            gl_FragColor = color;
        }
    """.trimIndent()
    
    // Glitch effect shader
    val GLITCH_SHADER = """
        precision mediump float;
        varying vec2 vTextureCoord;
        uniform sampler2D sTexture;
        uniform float time;
        uniform float intensity;
        
        void main() {
            vec2 uv = vTextureCoord;
            
            // RGB shift
            float shift = intensity * 0.02;
            float r = texture2D(sTexture, uv + vec2(shift, 0.0)).r;
            float g = texture2D(sTexture, uv).g;
            float b = texture2D(sTexture, uv - vec2(shift, 0.0)).b;
            
            // Scan lines
            float scanLine = sin(uv.y * 800.0 + time * 10.0) * 0.04 * intensity;
            
            // Random blocks
            float blockNoise = fract(sin(dot(uv.xy * 10.0, vec2(12.9898, 78.233))) * 43758.5453);
            if (blockNoise > 0.95) {
                r = 1.0 - r;
                g = 1.0 - g;
                b = 1.0 - b;
            }
            
            vec4 color = vec4(r, g, b, 1.0);
            color.rgb += scanLine;
            gl_FragColor = color;
        }
    """.trimIndent()
    
    // VHS retro filter
    val VHS_SHADER = """
        precision mediump float;
        varying vec2 vTextureCoord;
        uniform sampler2D sTexture;
        uniform float time;
        
        void main() {
            vec2 uv = vTextureCoord;
            
            // Chromatic aberration
            float shift = 0.003;
            float r = texture2D(sTexture, uv + vec2(shift, 0.0)).r;
            float g = texture2D(sTexture, uv).g;
            float b = texture2D(sTexture, uv - vec2(shift, 0.0)).b;
            
            // Vignette
            float dist = distance(uv, vec2(0.5));
            float vignette = 1.0 - dist * 0.6;
            
            // Noise
            float noise = fract(sin(dot(uv.xy * time, vec2(12.9898, 78.233))) * 43758.5453) * 0.1;
            
            vec4 color = vec4(r, g, b, 1.0);
            color.rgb *= vignette;
            color.rgb += noise;
            
            // Fade to slightly blue
            color.b = mix(color.b, 1.0, 0.1);
            
            gl_FragColor = color;
        }
    """.trimIndent()
    
    // Cyberpunk neon filter
    val CYBERPUNK_SHADER = """
        precision mediump float;
        varying vec2 vTextureCoord;
        uniform sampler2D sTexture;
        uniform float time;
        
        void main() {
            vec4 color = texture2D(sTexture, vTextureCoord);
            
            // Neon boost
            color.r = pow(color.r, 0.8);
            color.g = pow(color.g, 0.9);
            color.b = pow(color.b, 0.7);
            
            // Increase contrast
            color.rgb = (color.rgb - 0.5) * 1.4 + 0.5;
            
            // Add pink/cyan tint
            color.r += 0.1;
            color.b += 0.15;
            
            // Scan lines
            float scanLine = sin(vTextureCoord.y * 400.0 + time * 5.0) * 0.03;
            color.rgb += scanLine;
            
            gl_FragColor = color;
        }
    """.trimIndent()
    
    // Beautify filter (skin smoothing)
    val BEAUTIFY_SHADER = """
        precision mediump float;
        varying vec2 vTextureCoord;
        uniform sampler2D sTexture;
        uniform float intensity;
        
        void main() {
            vec2 uv = vTextureCoord;
            float offset = 0.003;
            
            // Simple blur for skin smoothing
            vec4 color = texture2D(sTexture, uv) * 0.4;
            color += texture2D(sTexture, uv + vec2(offset, 0.0)) * 0.15;
            color += texture2D(sTexture, uv - vec2(offset, 0.0)) * 0.15;
            color += texture2D(sTexture, uv + vec2(0.0, offset)) * 0.15;
            color += texture2D(sTexture, uv - vec2(0.0, offset)) * 0.15;
            
            // Brighten
            color.rgb = color.rgb * 1.1;
            
            // Slight pink tint for skin
            color.r = mix(color.r, color.r * 1.1, intensity);
            
            gl_FragColor = color;
        }
    """.trimIndent()
    
    // Get shader for filter type
    fun getShader(type: FilterType): String {
        return when (type) {
            FilterType.CLARENDON -> CLARENDON_SHADER
            FilterType.GINGHAM -> GINGHAM_SHADER
            FilterType.MOON -> MOON_SHADER
            FilterType.LARK -> LARK_SHADER
            FilterType.GLITCH -> GLITCH_SHADER
            FilterType.VHS -> VHS_SHADER
            FilterType.CYBERPUNK -> CYBERPUNK_SHADER
            FilterType.BEAUTIFY -> BEAUTIFY_SHADER
            else -> """precision mediump float;
                varying vec2 vTextureCoord;
                uniform sampler2D sTexture;
                void main() {
                    gl_FragColor = texture2D(sTexture, vTextureCoord);
                }"""
        }
    }
}

/**
 * Built-in filter presets
 */
object BuiltInFilters {
    
    val Clarendon = CameraFilter(
        id = "clarendon",
        name = "Clarendon",
        description = "High contrast with cool tint",
        filterType = FilterType.CLARENDON,
        shaderCode = FilterShaders.CLARENDON_SHADER,
        isOfficial = true,
        accentColor = Color(0xFF4A90E2)
    )
    
    val Gingham = CameraFilter(
        id = "gingham",
        name = "Gingham",
        description = "Vintage fade with warm tones",
        filterType = FilterType.GINGHAM,
        shaderCode = FilterShaders.GINGHAM_SHADER,
        isOfficial = true,
        accentColor = Color(0xFFE8A87C)
    )
    
    val Moon = CameraFilter(
        id = "moon",
        name = "Moon",
        description = "Black & white high contrast",
        filterType = FilterType.MOON,
        shaderCode = FilterShaders.MOON_SHADER,
        isOfficial = true,
        accentColor = Color(0xFF808080)
    )
    
    val Lark = CameraFilter(
        id = "lark",
        name = "Lark",
        description = "Bright and vibrant",
        filterType = FilterType.LARK,
        shaderCode = FilterShaders.LARK_SHADER,
        isOfficial = true,
        accentColor = Color(0xFFFFB347)
    )
    
    val Glitch = CameraFilter(
        id = "glitch",
        name = "Glitch",
        description = "Digital distortion effect",
        filterType = FilterType.GLITCH,
        shaderCode = FilterShaders.GLITCH_SHADER,
        parameters = mapOf(
            "intensity" to FilterParameter("Intensity", ParameterType.SLIDER, 0.5f, 0f, 1f)
        ),
        isOfficial = true,
        accentColor = Color(0xFFFF0055)
    )
    
    val VHS = CameraFilter(
        id = "vhs",
        name = "VHS",
        description = "Retro VHS tape look",
        filterType = FilterType.VHS,
        shaderCode = FilterShaders.VHS_SHADER,
        isOfficial = true,
        accentColor = Color(0xFF9B59B6)
    )
    
    val Cyberpunk = CameraFilter(
        id = "cyberpunk",
        name = "Cyberpunk",
        description = "Neon futuristic style",
        filterType = FilterType.CYBERPUNK,
        shaderCode = FilterShaders.CYBERPUNK_SHADER,
        isOfficial = true,
        accentColor = Color(0xFF00FFFF)
    )
    
    val Beautify = CameraFilter(
        id = "beautify",
        name = "Beautify",
        description = "Smooth skin and brighten",
        filterType = FilterType.BEAUTIFY,
        shaderCode = FilterShaders.BEAUTIFY_SHADER,
        parameters = mapOf(
            "intensity" to FilterParameter("Smoothing", ParameterType.SLIDER, 0.6f, 0f, 1f)
        ),
        isOfficial = true,
        accentColor = Color(0xFFFF69B4)
    )
    
    val Normal = CameraFilter(
        id = "normal",
        name = "Normal",
        description = "No filter",
        filterType = FilterType.NORMAL,
        shaderCode = FilterShaders.getShader(FilterType.NORMAL),
        isOfficial = true,
        accentColor = Color(0xFFFFFFFF)
    )
    
    val AllFilters = listOf(
        Normal, Clarendon, Gingham, Moon, Lark,
        Glitch, VHS, Cyberpunk, Beautify
    )
    
    val OfficialPack = FilterPack(
        id = "official_pack",
        name = "OpenChat Official",
        description = "Essential filters for your stories",
        filters = AllFilters,
        creatorId = "openchat_official",
        creatorName = "OpenChat",
        isOfficial = true,
        tags = listOf("official", "essential", "trending")
    )
}

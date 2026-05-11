package com.vintagecam.camera.pipeline

import com.vintagecam.profiles.ShaderType

internal const val QUAD_VERTEX_SHADER = """
    attribute vec4 aPosition;
    attribute vec4 aTextureCoord;
    varying vec2 vTextureCoord;
    void main() {
        gl_Position = aPosition;
        vTextureCoord = aTextureCoord.xy;
    }
"""

internal const val CAMERA_COPY_FRAGMENT_SHADER = """
    #extension GL_OES_EGL_image_external : require
    precision highp float;
    varying vec2 vTextureCoord;
    uniform samplerExternalOES uTexture;
    uniform mat4 uTexMatrix;
    void main() {
        vec2 transformed = (uTexMatrix * vec4(vTextureCoord, 0.0, 1.0)).xy;
        gl_FragColor = texture2D(uTexture, transformed);
    }
"""

internal const val SIMPLE_TEXTURE_FRAGMENT_SHADER = """
    precision highp float;
    varying vec2 vTextureCoord;
    uniform sampler2D uTexture;
    void main() {
        gl_FragColor = texture2D(uTexture, vTextureCoord);
    }
"""

internal fun fragmentShaderSourceFor(type: ShaderType): String {
    return when (type) {
        ShaderType.COLOR_MATRIX -> COLOR_MATRIX_FRAGMENT_SHADER
        ShaderType.VIGNETTE -> VIGNETTE_FRAGMENT_SHADER
        ShaderType.GRAIN -> GRAIN_FRAGMENT_SHADER
        ShaderType.SCANLINES -> SCANLINES_FRAGMENT_SHADER
        ShaderType.CHROMATIC_ABERRATION -> CHROMATIC_ABERRATION_FRAGMENT_SHADER
        ShaderType.CRUSH_BLACKS -> CRUSH_BLACKS_FRAGMENT_SHADER
        ShaderType.SHARPEN -> SHARPEN_FRAGMENT_SHADER
        ShaderType.SHADOW_NOISE -> SHADOW_NOISE_FRAGMENT_SHADER
        ShaderType.DATE_STAMP -> DATE_STAMP_FRAGMENT_SHADER
    }
}

private const val COLOR_MATRIX_FRAGMENT_SHADER = """
    precision highp float;
    varying vec2 vTextureCoord;
    uniform sampler2D uTexture;
    uniform mat3 colorMatrix;
    uniform vec3 shadowTint;
    uniform vec3 highlightTint;
    void main() {
        vec4 color = texture2D(uTexture, vTextureCoord);
        vec3 rgb = color.rgb;
        float luma = dot(rgb, vec3(0.299, 0.587, 0.114));
        vec3 tinted = mix(shadowTint, highlightTint, smoothstep(0.25, 0.85, luma));
        rgb = colorMatrix * rgb + tinted;
        gl_FragColor = vec4(rgb, color.a);
    }
"""

private const val VIGNETTE_FRAGMENT_SHADER = """
    precision highp float;
    varying vec2 vTextureCoord;
    uniform sampler2D uTexture;
    uniform float intensity;
    uniform float feather;
    void main() {
        vec4 color = texture2D(uTexture, vTextureCoord);
        float distanceToCenter = distance(vTextureCoord, vec2(0.5, 0.5));
        float vignette = 1.0 - intensity * smoothstep(0.5 - feather, 0.5, distanceToCenter);
        gl_FragColor = vec4(color.rgb * vignette, color.a);
    }
"""

private const val GRAIN_FRAGMENT_SHADER = """
    precision highp float;
    varying vec2 vTextureCoord;
    uniform sampler2D uTexture;
    uniform float intensity;
    uniform float size;
    float noise(vec2 st) {
        return fract(sin(dot(st.xy, vec2(12.9898,78.233))) * 43758.5453123);
    }
    void main() {
        vec4 color = texture2D(uTexture, vTextureCoord);
        float luma = dot(color.rgb, vec3(0.299, 0.587, 0.114));
        float grain = noise(vTextureCoord * size + vec2(gl_FragCoord.x, gl_FragCoord.y)) - 0.5;
        color.rgb += grain * intensity * (1.2 - luma);
        gl_FragColor = color;
    }
"""

private const val SCANLINES_FRAGMENT_SHADER = """
    precision highp float;
    varying vec2 vTextureCoord;
    uniform sampler2D uTexture;
    uniform float intensity;
    void main() {
        vec4 color = texture2D(uTexture, vTextureCoord);
        if (mod(gl_FragCoord.y, 4.0) < 1.0) {
            color.rgb *= (1.0 - intensity);
        }
        gl_FragColor = color;
    }
"""

private const val CHROMATIC_ABERRATION_FRAGMENT_SHADER = """
    precision highp float;
    varying vec2 vTextureCoord;
    uniform sampler2D uTexture;
    uniform float amount;
    void main() {
        vec2 center = vec2(0.5, 0.5);
        vec2 direction = vTextureCoord - center;
        vec4 red = texture2D(uTexture, vTextureCoord + direction * amount);
        vec4 green = texture2D(uTexture, vTextureCoord);
        vec4 blue = texture2D(uTexture, vTextureCoord - direction * amount);
        gl_FragColor = vec4(red.r, green.g, blue.b, green.a);
    }
"""

private const val CRUSH_BLACKS_FRAGMENT_SHADER = """
    precision highp float;
    varying vec2 vTextureCoord;
    uniform sampler2D uTexture;
    uniform float amount;
    void main() {
        vec4 color = texture2D(uTexture, vTextureCoord);
        color.rgb = pow(color.rgb, vec3(1.0 + amount));
        gl_FragColor = color;
    }
"""

private const val SHARPEN_FRAGMENT_SHADER = """
    precision highp float;
    varying vec2 vTextureCoord;
    uniform sampler2D uTexture;
    uniform float amount;
    uniform vec2 texelSize;
    void main() {
        vec4 center = texture2D(uTexture, vTextureCoord);
        vec4 left = texture2D(uTexture, vTextureCoord - vec2(texelSize.x, 0.0));
        vec4 right = texture2D(uTexture, vTextureCoord + vec2(texelSize.x, 0.0));
        vec4 up = texture2D(uTexture, vTextureCoord + vec2(0.0, texelSize.y));
        vec4 down = texture2D(uTexture, vTextureCoord - vec2(0.0, texelSize.y));
        vec3 sharpened = center.rgb * (1.0 + 4.0 * amount) - (left.rgb + right.rgb + up.rgb + down.rgb) * amount;
        gl_FragColor = vec4(sharpened, center.a);
    }
"""

private const val SHADOW_NOISE_FRAGMENT_SHADER = """
    precision highp float;
    varying vec2 vTextureCoord;
    uniform sampler2D uTexture;
    uniform float amount;
    float noise(vec2 st) {
        return fract(sin(dot(st.xy, vec2(12.9898,78.233))) * 43758.5453123);
    }
    void main() {
        vec4 color = texture2D(uTexture, vTextureCoord);
        float n = noise(vTextureCoord * 2048.0) - 0.5;
        color.rgb += vec3(n * amount * 0.08);
        gl_FragColor = color;
    }
"""

private const val DATE_STAMP_FRAGMENT_SHADER = """
    precision highp float;
    varying vec2 vTextureCoord;
    uniform sampler2D uTexture;
    uniform float amount;
    void main() {
        gl_FragColor = texture2D(uTexture, vTextureCoord);
    }
"""

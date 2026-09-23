package com.polypaint.app.render

/**
 * Metallic/roughness Cook-Torrance PBR shader (GLSL ES 3.00), one
 * directional key light plus a cheap hemisphere ambient term standing in
 * for full image-based lighting - see README for why. Six sampler inputs
 * map directly onto PaintChannel: albedo, normal, roughness, metallic,
 * ambient occlusion, emissive.
 */
object PbrShaders {

    const val VERTEX_SHADER = """#version 300 es
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aTexCoord;
layout(location = 3) in vec4 aTangent;

uniform mat4 uModel;
uniform mat4 uView;
uniform mat4 uProjection;
uniform mat3 uNormalMatrix;

out vec3 vWorldPos;
out vec3 vNormal;
out vec2 vTexCoord;
out mat3 vTBN;

void main() {
    vec4 worldPos = uModel * vec4(aPosition, 1.0);
    vWorldPos = worldPos.xyz;
    vNormal = normalize(uNormalMatrix * aNormal);
    vTexCoord = aTexCoord;

    vec3 N = vNormal;
    vec3 T = normalize(uNormalMatrix * aTangent.xyz);
    T = normalize(T - dot(T, N) * N);
    vec3 B = cross(N, T) * aTangent.w;
    vTBN = mat3(T, B, N);

    gl_Position = uProjection * uView * worldPos;
}
"""

    const val FRAGMENT_SHADER = """#version 300 es
precision highp float;

in vec3 vWorldPos;
in vec3 vNormal;
in vec2 vTexCoord;
in mat3 vTBN;

uniform sampler2D uAlbedoMap;
uniform sampler2D uNormalMap;
uniform sampler2D uRoughnessMap;
uniform sampler2D uMetallicMap;
uniform sampler2D uAoMap;
uniform sampler2D uEmissiveMap;

uniform vec3 uCameraPos;
uniform vec3 uLightDir;
uniform vec3 uLightColor;
uniform vec3 uAmbientSky;
uniform vec3 uAmbientGround;

out vec4 fragColor;

const float PI = 3.14159265359;

float distributionGGX(vec3 N, vec3 H, float roughness) {
    float a = roughness * roughness;
    float a2 = a * a;
    float NdotH = max(dot(N, H), 0.0);
    float NdotH2 = NdotH * NdotH;
    float denom = (NdotH2 * (a2 - 1.0) + 1.0);
    denom = PI * denom * denom;
    return a2 / max(denom, 1e-7);
}

float geometrySchlickGGX(float NdotV, float roughness) {
    float r = roughness + 1.0;
    float k = (r * r) / 8.0;
    return NdotV / (NdotV * (1.0 - k) + k);
}

float geometrySmith(vec3 N, vec3 V, vec3 L, float roughness) {
    float NdotV = max(dot(N, V), 0.0);
    float NdotL = max(dot(N, L), 0.0);
    return geometrySchlickGGX(NdotV, roughness) * geometrySchlickGGX(NdotL, roughness);
}

vec3 fresnelSchlick(float cosTheta, vec3 F0) {
    return F0 + (1.0 - F0) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);
}

void main() {
    vec4 albedoSample = texture(uAlbedoMap, vTexCoord);
    vec3 albedo = pow(max(albedoSample.rgb, vec3(0.0)), vec3(2.2));
    float roughness = clamp(texture(uRoughnessMap, vTexCoord).r, 0.045, 1.0);
    float metallic = texture(uMetallicMap, vTexCoord).r;
    float ao = texture(uAoMap, vTexCoord).r;
    vec3 emissive = texture(uEmissiveMap, vTexCoord).rgb;

    vec3 tangentNormal = texture(uNormalMap, vTexCoord).rgb * 2.0 - 1.0;
    vec3 N = normalize(vTBN * tangentNormal);
    vec3 V = normalize(uCameraPos - vWorldPos);
    vec3 L = normalize(-uLightDir);
    vec3 H = normalize(V + L);

    vec3 F0 = mix(vec3(0.04), albedo, metallic);

    float NDF = distributionGGX(N, H, roughness);
    float G = geometrySmith(N, V, L, roughness);
    vec3 F = fresnelSchlick(max(dot(H, V), 0.0), F0);

    vec3 numerator = NDF * G * F;
    float denom = 4.0 * max(dot(N, V), 0.0) * max(dot(N, L), 0.0) + 1e-4;
    vec3 specular = numerator / denom;

    vec3 kD = (vec3(1.0) - F) * (1.0 - metallic);
    float NdotL = max(dot(N, L), 0.0);
    vec3 directLight = (kD * albedo / PI + specular) * uLightColor * NdotL;

    float hemiFactor = clamp(N.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 ambient = mix(uAmbientGround, uAmbientSky, hemiFactor) * albedo * ao;

    vec3 color = ambient + directLight + emissive;
    color = color / (color + vec3(1.0));
    color = pow(color, vec3(1.0 / 2.2));

    fragColor = vec4(color, albedoSample.a);
}
"""
}

#version 120
// TODO(port-26.2): DISABLED — GLSL 120 fixed-pipeline shader (gl_TexCoord/gl_Color/gl_FragColor,
// uniform sampler2D texture). 26.2 core shaders are GLSL 450 with UBO bind groups and are declared from a
// RenderPipeline; this file is not referenced from any java code in the mod and is kept only as a record of
// the old preview transparency trick (see BlueprintRenderer.TransparencyHack).

uniform sampler2D texture;
uniform float alpha_multiplier;

void main() {
    vec4 tex = texture2D(texture, gl_TexCoord[0].xy) * gl_Color;
    gl_FragColor = vec4(tex.r, tex.g, tex.b, tex.a * alpha_multiplier);
}

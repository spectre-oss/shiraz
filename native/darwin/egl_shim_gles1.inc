// SPDX-License-Identifier: GPL-3.0-only
// Included by native/egl_shim.cpp on macOS only, inside its anonymous
// namespace, after realProc() and the per-thread current state.
//
// The shim backs an Android window with its own framebuffer object. Mesa
// accepts the core ES 2 framebuffer and renderbuffer entry points in any
// context; ANGLE answers GL_INVALID_OPERATION for them in an ES 1 context and
// accepts only their GL_OES_framebuffer_object forms there (checked on ANGLE
// chromium/8127 Metal: glGenRenderbuffers fails with 0x502, glGenRenderbuffersOES
// builds a complete RGBA8 + DEPTH24_STENCIL8 framebuffer). The OES enums have
// the core values, so only the entry points change. Each call checks the
// context that is current now (glGetString, no cache: ANGLE reuses context
// handles), so ES 2/3 contexts keep the core entry points.
// libGLESv2 is weakly linked: without ANGLE glGetString and glFinish are null.
// Read them through volatile pointers so the null checks are not folded away
// (the GLES headers do not declare them weak).
bool darwinGlesLoaded() {
    const GLubyte* (*volatile getString)(GLenum) = glGetString;
    void (*volatile finish)() = glFinish;
    return getString != nullptr && finish != nullptr;
}
bool darwinEs1Current() {
    // No ANGLE: keep the Linux path, where a missing glBindFramebuffer makes
    // the bind and the swap fail (EGL_FALSE) rather than call address 0.
    if (!darwinGlesLoaded()) return false;
    const char* version = reinterpret_cast<const char*>(glGetString(GL_VERSION));
    return version && (std::strncmp(version, "OpenGL ES 1.", 12) == 0 || std::strncmp(version, "OpenGL ES-CM 1.", 15) == 0);
}
template <typename Fn> Fn darwinFramebufferEntry(Fn core, const char* oes) {
    if (!darwinEs1Current()) return core;
    Proc proc = realProc();
    return proc ? reinterpret_cast<Fn>(proc(oes)) : nullptr;
}
void darwinGenRenderbuffers(GLsizei count, GLuint* names) {
    if (auto fn = darwinFramebufferEntry<PFNGLGENRENDERBUFFERSPROC>(glGenRenderbuffers, "glGenRenderbuffersOES")) fn(count, names);
}
void darwinDeleteRenderbuffers(GLsizei count, const GLuint* names) {
    if (auto fn = darwinFramebufferEntry<PFNGLDELETERENDERBUFFERSPROC>(glDeleteRenderbuffers, "glDeleteRenderbuffersOES")) fn(count, names);
}
void darwinBindRenderbuffer(GLenum target, GLuint name) {
    if (auto fn = darwinFramebufferEntry<PFNGLBINDRENDERBUFFERPROC>(glBindRenderbuffer, "glBindRenderbufferOES")) fn(target, name);
}
void darwinRenderbufferStorage(GLenum target, GLenum format, GLsizei width, GLsizei height) {
    if (auto fn = darwinFramebufferEntry<PFNGLRENDERBUFFERSTORAGEPROC>(glRenderbufferStorage, "glRenderbufferStorageOES")) fn(target, format, width, height);
}
void darwinGenFramebuffers(GLsizei count, GLuint* names) {
    if (auto fn = darwinFramebufferEntry<PFNGLGENFRAMEBUFFERSPROC>(glGenFramebuffers, "glGenFramebuffersOES")) fn(count, names);
}
void darwinDeleteFramebuffers(GLsizei count, const GLuint* names) {
    if (auto fn = darwinFramebufferEntry<PFNGLDELETEFRAMEBUFFERSPROC>(glDeleteFramebuffers, "glDeleteFramebuffersOES")) fn(count, names);
}
void darwinFramebufferRenderbuffer(GLenum target, GLenum attachment, GLenum renderbufferTarget, GLuint renderbuffer) {
    if (auto fn = darwinFramebufferEntry<PFNGLFRAMEBUFFERRENDERBUFFERPROC>(glFramebufferRenderbuffer, "glFramebufferRenderbufferOES")) fn(target, attachment, renderbufferTarget, renderbuffer);
}
GLenum darwinCheckFramebufferStatus(GLenum target) {
    auto fn = darwinFramebufferEntry<PFNGLCHECKFRAMEBUFFERSTATUSPROC>(glCheckFramebufferStatus, "glCheckFramebufferStatusOES");
    return fn ? fn(target) : 0;
}
// The ES 1 counterpart of dlsym(RTLD_NEXT, "glBindFramebuffer").
void (*darwinOesBindFramebuffer())(GLenum, GLuint) {
    static void (*const bind)(GLenum, GLuint) = [] {
        Proc proc = realProc();
        return proc ? reinterpret_cast<void (*)(GLenum, GLuint)>(proc("glBindFramebufferOES")) : nullptr;
    }();
    return bind;
}
// The rest of egl_shim.cpp calls these names only; none is defined there.
#define glGenRenderbuffers darwinGenRenderbuffers
#define glDeleteRenderbuffers darwinDeleteRenderbuffers
#define glBindRenderbuffer darwinBindRenderbuffer
#define glRenderbufferStorage darwinRenderbufferStorage
#define glGenFramebuffers darwinGenFramebuffers
#define glDeleteFramebuffers darwinDeleteFramebuffers
#define glFramebufferRenderbuffer darwinFramebufferRenderbuffer
#define glCheckFramebufferStatus darwinCheckFramebufferStatus

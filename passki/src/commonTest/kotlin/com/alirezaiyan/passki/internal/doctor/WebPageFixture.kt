package com.alirezaiyan.passki.internal.doctor

/**
 * What a single-page app's catch-all route serves for any path. The same body is used by the Gradle plugin and
 * passki-web tests, so all three consumers are checked against one response.
 */
internal const val SPA_INDEX_HTML: String =
    "<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\"><title>App</title></head>" +
        "<body><div id=\"root\"></div><script src=\"/assets/index.js\"></script></body></html>\n"

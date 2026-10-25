// Local mode (`./gradlew :webApp:wasmJsBrowserDevelopmentRun`): serve on localhost:8095 and proxy the demo API to a
// passki-web started with PASSKI_RP_ID=localhost on port 3017, so the page and its RP share one origin.
if (config.devServer) {
    config.devServer.port = 8095;
    config.devServer.open = false;
    config.devServer.proxy = [{ context: ["/api"], target: "http://localhost:3017" }];
}

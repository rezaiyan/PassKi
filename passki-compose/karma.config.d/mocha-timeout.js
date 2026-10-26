// Mocha's 2-second default is too short in headless Chrome: the first test of a fresh page also loads the test
// bundle, and the fake's WebCrypto work can take longer. Each test still has its own runTest timeout.
config.set({
  client: {
    mocha: {
      timeout: 10000,
    },
  },
});

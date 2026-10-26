# Japan27 TV

A tiny Fire TV / Android TV app for the Klein family's Japan 2027 trip. It shows https://jp27.netlify.app full screen and opens YouTube links in the native YouTube app.

- **Install / update:** see [INSTALL.md](INSTALL.md). In Downloader, go to `jp27.netlify.app/tv`.
- **Release:** push a tag (`git tag v0.2 && git push origin v0.2`). GitHub Actions builds a signed APK and attaches it to the release.
- **Web-app contract:** UA suffix `Japan27TV/<version>`, `window.Japan27TV` bridge, and `window.japan27TvBack()` for the Back button (see `MainActivity.kt`).

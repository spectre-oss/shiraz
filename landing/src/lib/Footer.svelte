<script>
  // SPDX-License-Identifier: GPL-3.0-only
  import { resolve } from '$app/paths';
  const home = resolve('/').replace(/\/$/, '') + '/';
</script>
  <footer class="site-footer wrap">
    <div class="footer-main">
      <div class="footer-identity"><a class="brand" href={home} aria-label="Shiraz home"><img src={home + 'spectre.svg'} width="42" height="42" alt=""><span>shiraz</span></a><p>Android™ apps.<br>A little more at home on Linux and macOS.</p></div>
      <nav class="footer-links" aria-label="Footer navigation">
        <div><h2>Explore</h2><a href={resolve('/compatibility/')}>App compatibility</a><a href={home + '#approach'}>How it works</a><a href={home + '#web'}>Shiraz Web</a><a href={home + '#mac'}>Shiraz Launcher for Mac</a><a href={home + '#compare'}>Compare approaches</a></div>
        <div><h2>Build with us</h2><a href="https://github.com/spectre-oss/shiraz">Source code</a><a href="https://github.com/spectre-oss/shiraz/releases">Releases</a><a href="https://github.com/spectre-oss/shiraz/issues">Report an issue</a></div>
      </nav>
    </div>
    <div class="footer-bottom"><a href="https://github.com/spectre-oss">A spectre-oss project <svg class="icon" data-icon="external" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25"/></svg></a><a href="https://github.com/spectre-oss/shiraz/blob/main/LICENSE">Open source · GPLv3</a><a class="back-to-top" href="#top">Back to top <svg class="icon" data-icon="arrow" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M13.5 4.5 21 12m0 0-7.5 7.5"/><path d="M21 12H3"/></svg></a></div>
    <div class="footer-notices">
      <p><a href="https://developer.android.com/legal">Android™ is a trademark of Google LLC.</a> Other product names and trademarks belong to their respective owners. Shiraz is an independent project; references to other projects or companies do not imply affiliation or endorsement.</p>
      <p>Shiraz’s original code is GPL-3.0-only. Third-party code, libraries, and fonts retain their own licenses and copyright notices. <a href="https://github.com/spectre-oss/shiraz/tree/main/LICENSES">Third-party notices</a> · <a href="https://github.com/spectre-oss/shiraz#credits--license">Credits &amp; acknowledgments</a></p>
    </div>
  </footer>

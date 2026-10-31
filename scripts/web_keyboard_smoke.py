#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Check browser keyboard focus against a served Shiraz page using Chromium/Selenium.

Only the WebRTC transport is stubbed; no commands reach an application.
Requires chromium, chromium-driver and Python selenium on the test host.
"""
import argparse
from selenium import webdriver
from selenium.webdriver.common.action_chains import ActionChains
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--url', default='http://127.0.0.1:8080/')
args = parser.parse_args()
options = webdriver.ChromeOptions()
options.add_argument('--headless=new')
options.add_argument('--no-sandbox')
options.add_argument('--disable-dev-shm-usage')
options.add_argument('--window-size=1400,1000')
driver = webdriver.Chrome(options=options)
try:
    driver.execute_cdp_cmd('Page.addScriptToEvaluateOnNewDocument', {'source': '''
window.sentCommands=[];
window.RTCPeerConnection=class {
 createDataChannel(){return {readyState:'open',send(value){sentCommands.push(JSON.parse(value))}}}
 addTransceiver(){}
 createOffer(){return new Promise(()=>{})}
};
'''})
    driver.get(args.url)
    driver.execute_script("connected=true;videoReady=true;setRunning(true)")
    screen = driver.find_element(By.ID, 'screen')
    outside = driver.find_element(By.ID, 'app-name')
    def commands():
        return driver.execute_script('return window.sentCommands')
    def clear():
        driver.execute_script('window.sentCommands=[]')
    ActionChains(driver).move_to_element(screen).click().move_to_element(outside).send_keys('a').perform()
    assert driver.execute_script('return document.activeElement.id') == 'screen'
    assert any(c['action'] == 'text' and c['text'] == 'a' for c in commands()), 'typing lost after pointer leaves'
    clear()
    ActionChains(driver).key_down(Keys.SHIFT).click(outside).perform()
    assert any(c['action'] == 'keyup' and c['key'] == 'ShiftLeft' for c in commands()), 'blur leaves key held'
    ActionChains(driver).key_up(Keys.SHIFT).perform()
    clear()
    ActionChains(driver).send_keys('b').perform()
    assert not commands(), 'typing outside remote screen was captured'
    driver.find_element(By.ID, 'portal-tab').click()
    assert driver.execute_script('return document.activeElement.id') == 'screen'
    clear()
    ActionChains(driver).send_keys('c').perform()
    assert any(c['action'] == 'text' and c['text'] == 'c' for c in commands()), 'Next field did not restore keyboard focus'
    clear()
    driver.execute_script('setRunning(false)')
    ActionChains(driver).send_keys('d').perform()
    assert not commands(), 'inactive session received keyboard input'
    print('WEB_KEYBOARD_FOCUS_OK')
finally:
    driver.quit()

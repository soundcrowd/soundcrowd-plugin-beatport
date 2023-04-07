# soundcrowd-plugin-beatport

[![android](https://github.com/soundcrowd/soundcrowd-plugin-beatport/actions/workflows/android.yml/badge.svg)](https://github.com/soundcrowd/soundcrowd-plugin-beatport/actions/workflows/android.yml)
[![GitHub release](https://img.shields.io/github/release/soundcrowd/soundcrowd-plugin-beatport.svg)](https://github.com/soundcrowd/soundcrowd-plugin-beatport/releases)
[![GitHub](https://img.shields.io/github/license/soundcrowd/soundcrowd-plugin-beatport.svg)](LICENSE)

This soundcrowd plugin adds basic Beatport support. It allows you to listen and browse music by genre, Top 100, curated playlists, your own playlists and additionally supports searching for music. This plugin requires a Beatport account and you need a Beatport subscription to listen to full-length tracks. Without subscription, you can only listen to the 2 minute samples.

## Note

In order to build this plugin, you need your own Beatport API key. Update the `client_id` and `redirect_uri` resources in the `build.gradle` before building.

## Building

    $ git clone --recursive https://github.com/soundcrowd/soundcrowd-plugin-beatport
    $ cd soundcrowd-plugin-beatport
    $ ./gradlew assembleDebug

Install via ADB:

    $ adb install beatport/build/outputs/apk/debug/beatport-debug.apk

## License

Licensed under GPLv3.

# MediaRebuild v1

Clean-room Android reimplementation inspired only by observable downloader behavior from the supplied APK.

## Included
- Paste/share URL input and generic public-page media discovery
- Direct HTTP video/file download with Range resume and 206 validation
- HLS VOD: master playlist, AES-128 key download, EXT-X-MAP, common BYTERANGE, concurrent segment downloading
- HLS remux to MP4 through FFmpegKit without re-encoding
- SQLite task history/state
- Foreground download service, cancel action, up to 3 top-level jobs
- MediaStore publication to the system video library

## Not copied
No original icon, logo, brand name, interface assets, proprietary API keys, private parser service, or protected source code are included.

Use only for content you are authorized to save. The app does not implement DRM or access-control bypass.

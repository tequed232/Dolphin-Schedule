import { mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

// 生成独立包的临时覆盖配置；不会改变正式包、默认启动入口或主清单。
const root = fileURLToPath(new URL('../../', import.meta.url));
const output = path.join(root, 'build', 'device-preview');
await mkdir(output, { recursive: true });
await writeFile(path.join(output, 'init.gradle'), `gradle.beforeProject { p ->
    if (p.path == ':app') p.plugins.withId('com.android.application') {
        p.extensions.getByName('androidComponents').finalizeDsl { android ->
            android.defaultConfig.applicationId = 'com.dolphin.schedule.devicepreview'
            android.sourceSets.getByName('debug').manifest.srcFile(new File(p.rootDir, 'build/device-preview/AndroidManifest.xml'))
        }
    }
}
`);
await writeFile(path.join(output, 'AndroidManifest.xml'), `<manifest xmlns:android="http://schemas.android.com/apk/res/android" xmlns:tools="http://schemas.android.com/tools">
    <application android:label="Dolphin Schedule 原生验收" tools:replace="android:label">
    </application>
</manifest>
`);
console.log('已生成独立验收配置：build/device-preview/init.gradle；仅传入 -I 时生效。');

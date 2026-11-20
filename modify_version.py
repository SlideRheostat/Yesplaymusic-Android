import sys
sys.stdout.reconfigure(encoding='utf-8')
with open('app/build.gradle.kts', 'r', encoding='utf-8') as f:
    c = f.read()
c = c.replace('versionName = "3.3.5"', 'versionName = "3.3.6"')
c = c.replace('versionCode = 57', 'versionCode = 58')
with open('app/build.gradle.kts', 'w', encoding='utf-8') as f:
    f.write(c)
print('版本号已修改为 3.3.6')

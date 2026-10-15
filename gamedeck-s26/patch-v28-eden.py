from pathlib import Path
import sys
root=Path(sys.argv[1])
file=root/"src/android/app/build.gradle.kts"
s=file.read_text(encoding="utf-8")
needle='    implementation("androidx.core:core-ktx:1.15.0")'
if s.count(needle)!=1:raise RuntimeError("v28 Eden Gradle dependency anchor")
s=s.replace(needle,needle+'\n    implementation("com.google.mlkit:text-recognition:16.0.1")',1)
file.write_text(s,encoding="utf-8")
print("v28: bundled on-device Latin text recognition in Android app build")

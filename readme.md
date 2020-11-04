### abt
android bluetooth helper
#### why?
did not find a (good) library for classic bluetooth stuff
#### features
##### classic
- [x] server
- [x] client
- [x] discovery with interface callbacks
##### le
none
#### example
[see this](https://github.com/nbdy/android_contacts/blob/master/app/src/main/java/io/eberlein/contacts/ui/FragmentSync.java)
#### gradle
[jitpack](https://jitpack.io/#nbdy/abt/)
```
// top level build.gradle
allprojects {
  repositories {
    ...
    maven { url 'https://jitpack.io' }
  }
}
// project build.gradle
dependencies {
  ...
  implementation 'com.github.smthnspcl:abt:master-SNAPSHOT'
}
```

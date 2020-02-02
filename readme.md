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
[see this](https://github.com/smthnspcl/android_contacts/blob/master/app/src/main/java/io/eberlein/contacts/ui/FragmentSync.java)
#### gradle
[jitpack](https://jitpack.io/#smthnspcl/abt/)
```
// top level build.gradle
allprojects {
  repositories {
    ...
    maven { url 'https://jitpack.io' }
  }
}
// project build.gradle
// {current-version} is (as of 02.02.20) 4d87495bc9, but check jitpack to be sure
dependencies {
  ...
  implementation 'com.github.smthnspcl:abt:{current-version}'
}
```

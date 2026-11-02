use jni::objects::{JObject, JString};
use jni::sys::jlong;
use jni::JNIEnv;

fn fnv1a(text: &str) -> u64 {
    let mut hash: u64 = 0xcbf29ce484222325;
    for byte in text.bytes() {
        hash ^= u64::from(byte);
        hash = hash.wrapping_mul(0x100000001b3);
    }
    hash
}

#[no_mangle]
pub extern "system" fn Java_sample_tasktide_client_NativeHash_fnv1a(
    mut env: JNIEnv,
    _this: JObject,
    text: JString,
) -> jlong {
    let text: String = env.get_string(&text).map(Into::into).unwrap_or_default();
    fnv1a(&text) as jlong
}

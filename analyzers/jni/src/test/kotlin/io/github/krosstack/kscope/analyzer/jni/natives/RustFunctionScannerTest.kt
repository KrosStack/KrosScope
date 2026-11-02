package io.github.krosstack.kscope.analyzer.jni.natives

import io.github.krosstack.kscope.model.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals

private const val HASH_LIBRARY = """use jni::objects::{JObject, JString};
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
"""

class RustFunctionScannerTest {
    private fun scan(text: String): List<NativeFunction> = RustFunctionScanner(text).scan()

    private fun names(text: String): List<String> = scan(text).map { it.name }

    @Test
    fun `the function of the reference library is a definition with the places of its name and its body`() {
        val function = scan(HASH_LIBRARY).single()
        val name = "Java_sample_tasktide_client_NativeHash_fnv1a"
        assertEquals(name, function.name)
        assertEquals(NativeRole.DEFINITION, function.role)
        assertEquals(TextRange(15, 24, 15, 24 + name.length), function.nameRange)
        assertEquals(TextRange(15, 1, 22, 2), function.declaration)
        assertEquals(
            "pub extern \"system\" fn $name(mut env: JNIEnv, _this: JObject, text: JString,) -> jlong",
            function.signature,
        )
    }

    @Test
    fun `lifetime parameters, unsafe attributes and the C calling convention are accepted`() {
        val text = """
            #[unsafe(no_mangle)]
            pub unsafe extern "C" fn Java_p_C_first<'local>(env: JNIEnv<'local>, class: JClass<'local>) -> jint { 0 }

            #[no_mangle]
            pub(crate) extern "system" fn Java_p_C_second(env: *mut JNIEnv, class: jclass) {}
        """.trimIndent()
        val (first, second) = scan(text)
        assertEquals(
            "pub unsafe extern \"C\" fn Java_p_C_first<'local>(env: JNIEnv<'local>, class: JClass<'local>) -> jint",
            first.signature,
        )
        assertEquals(TextRange(2, 1, 2, 106), first.declaration)
        assertEquals("extern \"system\" fn Java_p_C_second(env: *mut JNIEnv, class: jclass)", second.signature)
        assertEquals(TextRange(5, 12, 5, 82), second.declaration)
    }

    @Test
    fun `functions inside module blocks are found in the order of the file`() {
        val text = """
            mod android {
                pub extern "C" fn Java_p_C_inside() {}
            }
            pub extern "C" fn Java_p_C_outside() {}
        """.trimIndent()
        assertEquals(listOf("Java_p_C_inside", "Java_p_C_outside"), names(text))
    }

    @Test
    fun `a function inside another function, an impl block, a macro or a foreign block is not an item`() {
        val text = """
            fn outer() { pub extern "C" fn Java_p_C_nested() {} }
            impl Bridge { pub extern "C" fn Java_p_C_method() {} }
            export! { pub extern "C" fn Java_p_C_macro() {} }
            extern "C" { pub fn Java_p_C_foreign(x: i32); }
            pub extern "C" fn Java_p_C_found() {}
        """.trimIndent()
        assertEquals(listOf("Java_p_C_found"), names(text))
    }

    @Test
    fun `a macro invocation with any delimiter and a macro definition define no function`() {
        val text = """
            discard!(pub extern "C" fn Java_p_C_paren() {});
            discard![pub extern "C" fn Java_p_C_bracket() {}];
            macro_rules! make { () => { pub extern "C" fn Java_p_C_rule() {} }; }
            macro_rules! other ( () => ( pub extern "C" fn Java_p_C_other() {} ); );
            #[no_mangle]
            pub extern "C" fn Java_p_C_found() {}
        """.trimIndent()
        assertEquals(listOf("Java_p_C_found"), names(text))
    }

    @Test
    fun `comments and literals hide a function`() {
        val text = """
            // pub extern "C" fn Java_p_C_line() {}
            /* /* pub extern "C" fn Java_p_C_block() {} */ pub extern "C" fn Java_p_C_still() {} */
            const A: &str = "pub extern \"C\" fn Java_p_C_string() {}";
            const B: &str = r#"pub extern "C" fn Java_p_C_raw() {}"#;
            pub extern "C" fn Java_p_C_found() {}
        """.trimIndent()
        assertEquals(listOf("Java_p_C_found"), names(text))
    }

    @Test
    fun `a function without the extern qualifier or without a body is not a native function`() {
        val text = """
            pub fn Java_p_C_plain() {}
            pub extern "C" fn Java_p_C_prototype();
            pub extern "C" fn not_a_jni_name() {}
            pub extern "C" fn Java_p_C_found() {}
        """.trimIndent()
        assertEquals(listOf("Java_p_C_found"), names(text))
    }

    @Test
    fun `a library without such a function gives nothing`() {
        assertEquals(emptyList(), scan("fn main() { println!(\"Java_p_C_f\"); }"))
    }
}

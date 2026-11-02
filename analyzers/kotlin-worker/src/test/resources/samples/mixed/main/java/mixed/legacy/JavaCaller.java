package mixed.legacy;

import mixed.KotlinService;
import mixed.KotlinServiceKt;

public class JavaCaller {
    int call() {
        KotlinService service = KotlinService.create();
        return KotlinServiceKt.twice(service.getCounter());
    }
}

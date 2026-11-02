package rel.imports;

import static rel.imports.helpers.Constants.*;
import static rel.imports.helpers.HelpersKt.help;

import java.util.*;
import rel.imports.helpers.Tool;

public class JavaConsumer {
    int use() {
        List<Tool> tools = new ArrayList<>();
        return help() + help("x") + SIZE + tools.size();
    }
}

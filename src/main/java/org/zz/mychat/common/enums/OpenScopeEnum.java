package org.zz.mychat.common.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
public enum OpenScopeEnum {
    A("a"), B("b"), C("c");

    private String name;

    OpenScopeEnum(String name) {
        this.name = name;
    }

    public final static List<String> OpenScopeEnumNames = Arrays.stream(OpenScopeEnum.values())
            .map(OpenScopeEnum::getName).toList();

    public static boolean contains(String name) {
        return OpenScopeEnumNames.contains(name);
    }
}

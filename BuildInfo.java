package com.dsh.herald.release;

/** 正式版：不含任何个人数据。用户首次打开时自行填写接口与 API Key。 */
public final class BuildInfo {
    public static final boolean RELEASE = true;
    public static final String DEFAULT_TOKEN = "";

    private BuildInfo() { }
}

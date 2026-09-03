import type { Directory } from "./Interfaces/API";

const EXTENSION_ICON_MAP: Record<string, string> = {
    // Discs / disk images
    iso: "cd", bin: "cd", cue: "cd", nrg: "cd", img: "cd",

    // Installers / apps
    exe: "apps", apk: "apps", msi: "apps", dmg: "apps", deb: "apps", rpm: "apps",

    // Archives
    zip: "folderzip", rar: "folderzip", "7z": "folderzip", tar: "folderzip",
    gz: "folderzip", bz: "folderzip", bz2: "folderzip", xz: "folderzip",

    // Ebooks
    epub: "book", mobi: "book", azw: "book", azw3: "book", fb2: "book",

    // Presentations
    ppt: "slidetext", pptx: "slidetext", key: "slidetext", odp: "slidetext",

    // Markdown
    md: "documentmarkdown", markdown: "documentmarkdown",

    // PDF
    pdf: "documentpdf",

    // CSV
    csv: "documentcsv", tsv: "documentcsv",

    // Spreadsheets (grouped with bullet-list style doc icon)
    xls: "documentbulletlist", xlsx: "documentbulletlist", ods: "documentbulletlist",

    // Code
    js: "documentcode", ts: "documentcode", tsx: "documentcode", jsx: "documentcode",
    py: "documentcode", java: "documentcode", c: "documentcode", cpp: "documentcode",
    cs: "documentcode", go: "documentcode", rb: "documentcode", php: "documentcode",
    html: "documentcode", css: "documentcode", scss: "documentcode", json: "documentcode",
    xml: "documentcode", yml: "documentcode", yaml: "documentcode", sh: "documentcode",

    // Generic documents
    doc: "documentword", docx: "documentword", odt: "documentword", rtf: "documentword", txt: "documentword",
};

function getExtension(path: string): string {
    const dot = path.lastIndexOf(".");
    return dot === -1 ? "" : path.substring(dot + 1).toLowerCase();
}

export default function getImageToUse(data: Directory) {
    if (data.isDirectory) return "folder";

    switch (data.mimeType?.substring(0, data.mimeType.indexOf("/"))) {
        case "audio":
            return "musicnote1";
        case "image":
            return "image";
        case "video":
            return "videoclip";
    }

    if (data.mimeType === "application/vnd.amazon.ebook") return "book";
    if (data.mimeType === "application/vnd.android.package-archive") return "apps";
    if (data.mimeType === "application/pdf") return "documentpdf";
    if (data.mimeType === "text/csv") return "documentcsv";
    if (data.mimeType === "text/markdown") return "documentmarkdown";
    if (data.mimeType === "application/zip" || data.mimeType === "application/x-7z-compressed" || data.mimeType === "application/x-rar-compressed")
        return "folderzip";

    return EXTENSION_ICON_MAP[getExtension(data.path)] ?? "document";
}
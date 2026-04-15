# -*- coding: utf-8 -*-
"""
InterWise 知识原子批量生成器 (atomizer.py)

将普通文档 (PDF/DOCX/TXT/MD) 批量转化为符合项目规范的 JSON 知识原子，
直接输出到 backend/src/main/resources/knowledge_base/atoms/{category}/

使用说明:
    # 单文件转换
    python scripts/atomizer.py -f "doc.pdf" -c java

    # 批量转换整个目录下所有文件
    python scripts/atomizer.py -d "my_docs/" -c mysql

依赖:
    pip install openai PyPDF2 python-docx
"""

import os
import json
import re
import argparse
import time

# ============================================================
# 配置区 —— 仅使用 DeepSeek API
# ============================================================
BASE_URL   = "https://api.deepseek.com/v1"
API_KEY    = os.getenv("DEEPSEEK_API_KEY", "sk-4b4d8849b9234412a4f7e740e53f6f67")
MODEL_NAME = "deepseek-chat"

# 输出根目录（对应后端 classpath 下的知识库目录）
OUTPUT_ROOT = os.path.join(
    os.path.dirname(__file__),
    "..", "backend", "src", "main", "resources", "knowledge_base", "atoms","frontend"
)

SUPPORTED_EXTS = {".pdf", ".txt", ".md", ".docx"}
# ============================================================


def check_dependencies():
    """检查依赖库是否已安装"""
    missing = []
    try:
        import openai
    except ImportError:
        missing.append("openai")
    try:
        import PyPDF2
    except ImportError:
        missing.append("PyPDF2")
    try:
        import docx
    except ImportError:
        missing.append("python-docx")
    if missing:
        print(f"❌ 缺少依赖库，请运行: pip install {' '.join(missing)}")
        exit(1)


def extract_text(file_path: str) -> str:
    """从不同格式的文档中提取纯文本"""
    ext = os.path.splitext(file_path)[1].lower()
    text = ""

    if ext in (".txt", ".md"):
        with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
            text = f.read()

    elif ext == ".pdf":
        import PyPDF2
        with open(file_path, "rb") as f:
            reader = PyPDF2.PdfReader(f)
            for page in reader.pages:
                extracted = page.extract_text()
                if extracted:
                    text += extracted + "\n"

    elif ext == ".docx":
        from docx import Document
        doc = Document(file_path)
        text = "\n".join(p.text for p in doc.paragraphs if p.text.strip())

    return text.strip()


def chunk_text(text: str, chunk_size: int = 4000, overlap: int = 200) -> list[str]:
    """
    将长文本切分为带重叠的块，避免考点在切分边界处被截断。
    overlap: 相邻两块的重叠字符数，防止一个知识点被切成两半。
    """
    chunks = []
    start = 0
    while start < len(text):
        end = start + chunk_size
        chunks.append(text[start:end])
        start += chunk_size - overlap
    return chunks


def call_ai(text: str, category: str) -> str:
    """调用 LLM，将单个文本块转化为 JSON 知识原子数组字符串"""
    from openai import OpenAI
    client = OpenAI(api_key=API_KEY, base_url=BASE_URL)

    prompt = f"""你是一个资深技术面试官。请将下面的技术文档内容转化为 **一个或多个** InterWise 知识原子。

## 输出格式
必须严格输出以下格式的 JSON 数组（即使只有一个原子，也要用数组包裹），不要有任何 Markdown 代码块或解释文字：

[
  {{
    "id": "全局唯一的英文短ID，例如 java-hashmap-rehash",
    "subject": "考察的知识点标题",
    "category": "{category}",
    "difficulty": "简单 | 中等 | 困难",
    "tags": ["标签1", "标签2"],
    "content": {{
      "principles": "核心原理与标准答案，要求准确深入，可包含源码级细节",
      "pitfalls": [
        "候选人容易理解偏差或答错的点",
        "面试官常用来考察的'坑'"
      ],
      "follow_up_paths": [
        "如果候选人答得好，可以继续深挖的追问",
        "如果候选人答得不好，可以换角度引导的问题"
      ]
    }}
  }}
]

## 特别注意
- 若本段文字中不包含任何有价值的面试考点（如目录、广告、页眉页脚），请直接输出空数组 []
- 不要重复已经很相似的知识点，每个原子必须有独立的考察价值

## 输入文本
{text}
"""

    response = client.chat.completions.create(
        model=MODEL_NAME,
        messages=[
            {"role": "system", "content": "你是一个只输出合法 JSON 的技术文档处理引擎，不输出任何解释。"},
            {"role": "user", "content": prompt},
        ],
        temperature=0.3,
    )
    return response.choices[0].message.content.strip()



def parse_and_save(raw_json: str, category: str, source_file: str) -> int:
    """解析 AI 返回的 JSON 并保存文件，返回成功保存的原子数量"""
    # 清理可能存在的 Markdown 包裹
    cleaned = re.sub(r"^```(?:json)?\s*|\s*```$", "", raw_json, flags=re.MULTILINE).strip()

    atoms = json.loads(cleaned)
    # 兼容 AI 意外返回单个对象而非数组的情况
    if isinstance(atoms, dict):
        atoms = [atoms]

    save_dir = os.path.normpath(os.path.join(OUTPUT_ROOT, category))
    os.makedirs(save_dir, exist_ok=True)

    count = 0
    for atom in atoms:
        atom_id = atom.get("id", f"atom_{int(time.time())}_{count}")
        # ID 中不允许含斜杠等特殊字符，做一次清理
        safe_id = re.sub(r"[^\w\-]", "_", atom_id)
        save_path = os.path.join(save_dir, f"{safe_id}.json")

        with open(save_path, "w", encoding="utf-8") as f:
            json.dump(atom, f, ensure_ascii=False, indent=2)

        print(f"    ✅ 已保存: {os.path.relpath(save_path)} [{atom.get('subject', '')}]")
        count += 1

    return count


def process_file(file_path: str, category: str) -> bool:
    """处理单个文件（自动分块），返回是否成功"""
    print(f"\n📄 处理: {os.path.basename(file_path)}")

    text = extract_text(file_path)
    if not text:
        print("  ⚠️  未能提取到文字内容，跳过")
        return False

    chunks = chunk_text(text)
    total_chars = len(text)
    print(f"  📝 共 {total_chars} 字符，切分为 {len(chunks)} 块，逐块调用 AI...")

    total_atoms = 0
    for idx, chunk in enumerate(chunks, 1):
        print(f"  🔄 [{idx}/{len(chunks)}] 正在处理第 {idx} 块 ({len(chunk)} 字符)...")
        try:
            raw = call_ai(chunk, category)
            count = parse_and_save(raw, category, file_path)
            total_atoms += count
        except json.JSONDecodeError as e:
            print(f"    ⚠️  第 {idx} 块 JSON 解析失败，跳过: {e}")
        except Exception as e:
            print(f"    ❌ 第 {idx} 块调用失败: {e}")

    print(f"  🎉 共生成 {total_atoms} 个知识原子")
    return total_atoms > 0


def collect_files(path: str) -> list[str]:
    """收集单个文件或目录下所有支持格式的文件路径"""
    if os.path.isfile(path):
        return [path] if os.path.splitext(path)[1].lower() in SUPPORTED_EXTS else []

    result = []
    for root, _, files in os.walk(path):
        for name in files:
            if os.path.splitext(name)[1].lower() in SUPPORTED_EXTS:
                result.append(os.path.join(root, name))
    return result


def main():
    check_dependencies()

    parser = argparse.ArgumentParser(
        description="InterWise 知识原子批量生成器",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  # 单文件
  python scripts/atomizer.py -f notes/hashmap.pdf -c java

  # 整个目录批量处理
  python scripts/atomizer.py -d notes/mysql/ -c mysql
""",
    )
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("-f", "--file", help="单个输入文件路径 (pdf/txt/docx/md)")
    group.add_argument("-d", "--dir",  help="输入目录路径，批量处理其中所有支持格式的文件")
    parser.add_argument("-c", "--category", required=True,
                        help="知识分类，如 java / spring / mysql / redis / frontend 等")
    args = parser.parse_args()

    if not API_KEY:
        print("❌ 未检测到 API Key，请设置环境变量 AI_API_KEY 或 DEEPSEEK_API_KEY")
        exit(1)

    print("=" * 60)
    print(f"🚀 InterWise 知识原子生成器")
    print(f"   模型: {MODEL_NAME}  ({BASE_URL})")
    print(f"   分类: {args.category}")
    print(f"   输出: {os.path.normpath(os.path.join(OUTPUT_ROOT, args.category))}")
    print("=" * 60)

    source = args.file or args.dir
    files = collect_files(source)

    if not files:
        print(f"⚠️  在 '{source}' 中未找到支持的文件 (支持: {', '.join(SUPPORTED_EXTS)})")
        exit(0)

    print(f"\n共发现 {len(files)} 个文件，开始处理...\n")

    total_ok, total_fail = 0, 0
    for fp in files:
        try:
            ok = process_file(fp, args.category)
            if ok:
                total_ok += 1
            else:
                total_fail += 1
        except json.JSONDecodeError as e:
            print(f"  ❌ JSON 解析失败: {e}\n     (AI 返回了非法内容，可能是 Token 不足或模型格式不支持)")
            total_fail += 1
        except Exception as e:
            print(f"  ❌ 处理异常: {e}")
            total_fail += 1

    print("\n" + "=" * 60)
    print(f"✅ 完成！成功 {total_ok} 个 | 失败 {total_fail} 个")
    print("💡 重启后端服务后，新原子将自动加载进向量数据库。")
    print("=" * 60)


if __name__ == "__main__":
    main()

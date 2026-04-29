"""
Reclassify hot200 knowledge atoms:
1. Update category field in each JSON
2. Move files to correct directories
3. Report summary of changes
"""
import json
import os
import shutil
from pathlib import Path
from collections import defaultdict

ATOMS_DIR = Path(r"E:\Java-web\interview\backend\src\main\resources\knowledge_base\atoms")

# ─── Classification rules ───────────────────────────────────────────────
# Pattern: (prefix_pattern, target_subdir)
# Order matters — first match wins. Patterns match against filename.lower().

JAVA_BACKEND_RULES = [
    # ── Redis ──
    (["redis-", "redis_"], "redis"),
    # ── MySQL ──
    (["mysql-", "mysql_", "database-index-", "database-sharding", "db-isolation"],
     "mysql"),
    # ── Spring ──
    (["spring-", "springboot-", "spring_", "springboot_"], "spring"),
    # ── JVM ──
    (["jvm-", "gc_", "classloading", "java-jvm-", "java-gc-"],
     "jvm"),
    # ── Kafka ──
    (["kafka-"], "kafka"),
    # ── RabbitMQ ──
    (["rabbitmq-"], "rabbitmq"),
    # ── RocketMQ ──
    (["rocketmq-"], "rocketmq"),
    # ── Netty ──
    (["netty-", "netty_"], "netty"),
    # ── Message Queue ──
    (["mq-", "message-queue-"], "消息队列"),
    # ── MyBatis ──
    (["mybatis-"], "MyBatis"),
    # ── 网络 ──
    (["tcp-", "http-", "http1-", "http2-", "http3-", "https-",
      "osi-", "select-poll-", "unix-io-", "cookie-session-", "socket-"],
     "网络"),
    # ── 设计模式 ──
    (["design-pattern-", "observer-pattern", "simple-factory-", "strategy-pattern",
      "template-method-", "chain-of-responsibility", "singleton-pattern",
      "common-design-patterns"],
     "设计模式"),
    # ── 分布式 ──
    (["distributed-", "seata-", "consistent_hash", "load-balancing-",
      "rate-limiting-", "rpc-framework-"],
     "分布式"),
    # ── 微服务 ──
    (["microservice-", "api-gateway-", "config-center-", "service-degradation-",
      "service-degradation_strategy"],
     "微服务"),
    # ── 系统设计 ──
    (["system-design-", "seckill-", "short-url-", "like-system-"],
     "系统设计"),
    # ── 数据结构 ──
    (["data-structure-", "hashmap"],
     "数据结构"),
    # ── 并发 (catch java- prefixed concurrency items) ──
    (["java-", "concurrent", "juc_", "thread_pool", "synchronized-",
      "threadlocal", "aqs", "java-cas-", "java-concurrency-",
      "java-concurrent", "java-deadlock-", "java-final-",
      "java-lock-", "java-multithreading-", "java-singleton-",
      "java-thread-", "java-volatile-", "reactor-thread-"],
     "并发"),
    # ── 操作系统 ──
    (["linux-", "os-", "logical-physical-"],
     "操作系统"),
    # ── JVM (catch java-jvm patterns not caught above) ──
    (["java-online-cpu", "java-jvm-", "java-gc-"],
     "jvm"),
    # ── Java collection (data structure) ──
    (["java-collection", "java-hashmap"],
     "数据结构"),
]

FRONTEND_RULES = [
    # ── CSS ──
    (["css-", "theme-switch-"], "CSS"),
    # ── JavaScript ──
    (["js-", "es6-", "javascript-"], "JavaScript"),
    # ── NodeJS ──
    (["nodejs-", "npm-", "node-"], "NodeJS"),
    # ── Webpack ──
    (["webpack-", "live-reload-"], "Webpack"),
    # ── Vue ──
    (["vue-", "vue2-", "vue3-", "vuex-", "virtual-dom-"], "Vue"),
    # ── React ──
    (["react-"], "React"),
    # ── HTML ──
    (["html-", "html5-"], "HTML"),
    # ── 浏览器 ──
    (["browser-", "canvas-", "cross-browser-"], "浏览器"),
    # ── 网络 ──
    (["tcp-", "http2-", "http3-", "https-", "osi-", "cookie-session-"],
     "网络"),
    # ── 设计模式 ──
    (["design-pattern-", "common-design-patterns"], "设计模式"),
    # ── 前端工程化 ──
    (["frontend-", "spa-", "qr-code-", "websocket-",
      "pc-mobile-", "search-suggest-", "system-high-concurrency"],
     "前端工程化"),
    # ── 系统设计 ──
    (["seckill-", "system-"], "系统设计"),
    # ── TypeScript ──
    (["typescript-"], "TypeScript"),
    # ── 性能优化 ──
    (["core-web-vitals", "frontend-perf", "performance-monitoring"],
     "性能优化"),
    # ── 网络 ── (catch remaining TCP/HTTP)
    (["tcp-", "http-"], "网络"),
]

def classify(filename, rules):
    name = filename.lower().replace("_", "-")
    for patterns, category in rules:
        for p in patterns:
            pn = p.lower().replace("_", "-")
            if name.startswith(pn):
                return category
    return None

def process_atoms(src_dir, target_base, rules, stats):
    """Process all JSON files in src_dir, update category, move to target."""
    src = Path(src_dir)
    if not src.exists():
        print(f"  Directory not found: {src}")
        return

    for f in sorted(src.glob("*.json")):
        try:
            with open(f, "r", encoding="utf-8") as fh:
                data = json.load(fh)
        except Exception as e:
            print(f"  ERROR reading {f.name}: {e}")
            continue

        old_cat = data.get("category", "")
        new_cat = classify(f.stem, rules)
        if not new_cat:
            print(f"  UNCLASSIFIED: {f.name}  (old: {old_cat})")
            stats["unclassified"].append(str(f))
            continue

        # Update category
        data["category"] = new_cat

        # Determine target directory
        target_dir = target_base / new_cat
        target_dir.mkdir(parents=True, exist_ok=True)
        target_file = target_dir / f.name

        # Check for duplicate filename in target
        if target_file.exists() and target_file != f:
            print(f"  CONFLICT: {f.name} already exists in {new_cat}, renaming")
            target_file = target_dir / f"hot200_{f.name}"

        # Write updated JSON
        with open(target_file, "w", encoding="utf-8") as fh:
            json.dump(data, fh, ensure_ascii=False, indent=2)
            fh.write("\n")

        # Remove original
        if target_file != f:
            os.remove(f)

        stats["moved"].append((f.name, old_cat, new_cat))
        print(f"  {f.name}: {old_cat} → {new_cat}")

def main():
    stats = {"moved": [], "unclassified": []}
    per_category = defaultdict(int)

    # ── Process java_backend/hot200 ──
    print("=" * 60)
    print("Processing java_backend/hot200/...")
    process_atoms(
        ATOMS_DIR / "java_backend" / "hot200",
        ATOMS_DIR / "java_backend",
        JAVA_BACKEND_RULES,
        stats
    )

    # ── Process frontend/hot200 ──
    print("=" * 60)
    print("Processing frontend/hot200/...")
    process_atoms(
        ATOMS_DIR / "frontend" / "hot200",
        ATOMS_DIR / "frontend",
        FRONTEND_RULES,
        stats
    )

    # ── Summary ──
    print("\n" + "=" * 60)
    print("SUMMARY")
    print(f"  Total moved: {len(stats['moved'])}")
    print(f"  Unclassified: {len(stats['unclassified'])}")

    for fname, old, new in stats["moved"]:
        per_category[new] += 1

    print("\nFiles per category:")
    for cat in sorted(per_category.keys()):
        print(f"  {cat}: {per_category[cat]}")

    if stats["unclassified"]:
        print("\nUNCLASSIFIED files (need manual review):")
        for f in stats["unclassified"]:
            print(f"  {f}")

    # ── Remove empty hot200 dirs ──
    for hot_dir in [
        ATOMS_DIR / "java_backend" / "hot200",
        ATOMS_DIR / "frontend" / "hot200",
    ]:
        if hot_dir.exists():
            try:
                hot_dir.rmdir()
                print(f"\nRemoved empty dir: {hot_dir}")
            except OSError:
                print(f"\nDir not empty (some files left): {hot_dir}")

if __name__ == "__main__":
    main()

"""
面试智能助手 - 对话功能测试脚本
测试项：1) 流式对话+sessionId  2) 会话列表  3) 历史消息  4) 多轮对话  5) 继续会话

使用方法：
  1. 先重启后端（使用最新构建的代码）
  2. python test_chat.py

依赖：pip install requests
"""
import requests
import json
import sys
import io

# Windows GBK 编码兼容
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

BASE_URL = "http://localhost:8180/api"

def test_stream_chat():
    """测试1：流式对话 + 获取 sessionId"""
    print("=" * 60)
    print("测试1：流式对话 + 获取 sessionId")
    print("=" * 60)

    url = f"{BASE_URL}/chat/stream"
    params = {
        "message": "你好，请简单自我介绍",
        "domain": "Java",
        "difficulty": "中级",
        "thinkingEnabled": "false"
    }

    try:
        resp = requests.get(url, params=params, stream=True, timeout=60)
        resp.raise_for_status()

        session_id = None
        full_content = ""

        for line in resp.iter_lines(decode_unicode=True):
            if not line:
                continue
            if line.startswith("data:"):
                data = line[5:].strip()
                if data == "[DONE]":
                    continue
                # 尝试解析 sessionId（第一个事件）
                if session_id is None and data.startswith("{"):
                    try:
                        parsed = json.loads(data)
                        if "sessionId" in parsed:
                            session_id = parsed["sessionId"]
                            print(f"  [OK] 获取到 sessionId: {session_id}")
                            continue
                    except json.JSONDecodeError:
                        pass
                full_content += data

        print(f"  [OK] 流式内容长度: {len(full_content)} 字符")
        print(f"  [OK] 内容预览: {full_content[:100]}...")
        return session_id
    except Exception as e:
        print(f"  [FAIL] 流式对话失败: {e}")
        return None


def test_list_sessions():
    """测试2：会话列表"""
    print("\n" + "=" * 60)
    print("测试2：获取会话列表")
    print("=" * 60)

    try:
        resp = requests.get(f"{BASE_URL}/chat/sessions", timeout=10)
        result = resp.json()
        if result.get("code") == 200:
            sessions = result.get("data", [])
            print(f"  [OK] 共 {len(sessions)} 个会话")
            for s in sessions[:5]:
                print(f"     - sessionId={s['sessionId']}, domain={s.get('domain','')}, createdAt={s.get('createdAt','')}")
            return sessions
        else:
            print(f"  [FAIL] 获取会话列表失败: {result}")
            return []
    except Exception as e:
        print(f"  [FAIL] 请求失败: {e}")
        return []


def test_history_messages(session_id):
    """测试3：获取历史消息"""
    print("\n" + "=" * 60)
    print(f"测试3：获取会话 {session_id[:8]}... 的历史消息")
    print("=" * 60)

    try:
        resp = requests.get(f"{BASE_URL}/chat/sessions/{session_id}/messages", timeout=10)
        result = resp.json()
        if result.get("code") == 200:
            messages = result.get("data", [])
            print(f"  [OK] 共 {len(messages)} 条消息")
            for msg in messages[:6]:
                role = "[User]" if msg["role"] == "user" else "[AI]"
                content_preview = msg["content"][:60].replace("\n", " ")
                print(f"     {role}: {content_preview}...")
            return messages
        else:
            print(f"  [FAIL] 获取历史消息失败: {result}")
            return []
    except Exception as e:
        print(f"  [FAIL] 请求失败: {e}")
        return []


def test_multi_turn(session_id):
    """测试4：多轮对话（使用已有 sessionId）"""
    print("\n" + "=" * 60)
    print(f"测试4：多轮对话（继续会话 {session_id[:8]}...）")
    print("=" * 60)

    url = f"{BASE_URL}/chat/stream"
    params = {
        "message": "请再告诉我，Java中HashMap和ConcurrentHashMap的区别是什么？",
        "domain": "Java",
        "difficulty": "中级",
        "thinkingEnabled": "false",
        "sessionId": session_id
    }

    try:
        resp = requests.get(url, params=params, stream=True, timeout=60)
        resp.raise_for_status()

        full_content = ""
        got_session_id = False

        for line in resp.iter_lines(decode_unicode=True):
            if not line:
                continue
            if line.startswith("data:"):
                data = line[5:].strip()
                if data == "[DONE]":
                    continue
                if data.startswith("{"):
                    try:
                        parsed = json.loads(data)
                        if "sessionId" in parsed:
                            got_session_id = True
                            print(f"  [OK] sessionId 已返回: {parsed['sessionId'][:8]}...")
                            continue
                    except json.JSONDecodeError:
                        pass
                full_content += data

        print(f"  [OK] 多轮对话内容长度: {len(full_content)} 字符")
        print(f"  [OK] 内容预览: {full_content[:100]}...")
        return True
    except Exception as e:
        print(f"  [FAIL] 多轮对话失败: {e}")
        return False


def test_resume_session(session_id):
    """测试5：验证会话恢复 - 再次获取历史消息，确认多轮对话已存入"""
    print("\n" + "=" * 60)
    print("测试5：验证会话恢复 - 检查历史消息是否包含多轮对话")
    print("=" * 60)

    messages = test_history_messages(session_id)
    if len(messages) >= 4:
        print(f"  [OK] 历史消息共 {len(messages)} 条（>=4），多轮对话存储正常！")
        return True
    else:
        print(f"  [WARN] 历史消息仅 {len(messages)} 条，可能存储不完整")
        return False


def main():
    print("[TEST] 面试智能助手 - 对话功能测试")
    print("请确保后端已使用最新代码重启！\n")

    # 测试1：流式对话
    session_id = test_stream_chat()
    if not session_id:
        print("\n[FAIL] 测试1失败，无法继续后续测试")
        sys.exit(1)

    # 测试2：会话列表
    sessions = test_list_sessions()

    # 测试3：历史消息
    test_history_messages(session_id)

    # 测试4：多轮对话
    test_multi_turn(session_id)

    # 测试5：会话恢复验证
    test_resume_session(session_id)

    print("\n" + "=" * 60)
    print("[DONE] 测试完成！")
    print("=" * 60)


if __name__ == "__main__":
    main()

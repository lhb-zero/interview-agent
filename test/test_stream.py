import requests, sys, io, time
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

start = time.time()
r = requests.get('http://localhost:8180/api/chat/stream', params={
    'message': '请生成2道Java基础面试题',
    'domain': 'Java',
    'difficulty': '中级',
    'thinkingEnabled': 'false'
}, stream=True, timeout=60)

print('Status:', r.status_code)
session_id = None
full_content = ""
count = 0

for line in r.iter_lines(decode_unicode=True):
    if not line:
        continue
    if line.startswith("data:"):
        data = line[5:].strip()
        if data == "[DONE]":
            continue
        if session_id is None and data.startswith("{"):
            try:
                parsed = eval(data)
                if "sessionId" in parsed:
                    session_id = parsed["sessionId"]
                    print("SessionId:", session_id)
                    continue
            except:
                pass
        full_content += data
        count += 1
        if count <= 5:
            print("Chunk:", data[:50])

elapsed = time.time() - start
print("Total time: %.1fs" % elapsed)
print("Content length: %d chars" % len(full_content))
print("Content preview:", full_content[:200])

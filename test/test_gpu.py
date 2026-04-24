import requests, time, sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

start = time.time()
r = requests.post('http://localhost:11434/api/generate', json={
    'model': 'qwen3.5:4b',
    'prompt': '你好，请用一句话介绍自己',
    'stream': False,
    'options': {'num_predict': 50}
}, timeout=120)
elapsed = time.time() - start

print('Status:', r.status_code)
print('Time: %.1fs' % elapsed)

if r.status_code == 200:
    data = r.json()
    resp = data.get('response', '')
    eval_count = data.get('eval_count', 0)
    eval_duration = data.get('eval_duration', 0)
    print('Response:', resp[:200])
    print('Eval count:', eval_count, 'tokens')
    print('Eval duration: %.1fs' % (eval_duration / 1e9))
    if eval_duration > 0 and eval_count > 0:
        tps = eval_count / (eval_duration / 1e9)
        print('Speed: %.1f tokens/s' % tps)
else:
    print('Error:', r.text[:300])

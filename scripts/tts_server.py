"""
Edge TTS 本地代理服务
启动方式: python scripts/tts_server.py
监听地址: http://127.0.0.1:5050/?text=hello
语音列表可通过 edge-tts --list-voices 查看
"""
from http.server import BaseHTTPRequestHandler, HTTPServer
import urllib.parse
import asyncio
import edge_tts
import edge_tts.communicate as cm
from edge_tts.constants import TRUSTED_CLIENT_TOKEN

# 修复微软端点迁移导致的 403 错误
# 旧端点 speech.platform.bing.com 已逐步停用，切换到新端点 api.msedgeservices.com
cm.WSS_URL = f"wss://api.msedgeservices.com/tts/cognitiveservices/websocket/v1?Ocp-Apim-Subscription-Key={TRUSTED_CLIENT_TOKEN}"

VOICE = "en-US-AriaNeural"


class TTSHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        params = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
        text = params.get("text", [""])[0]
        voice = params.get("voice", [VOICE])[0]
        if not text:
            self.send_response(400)
            self.end_headers()
            self.wfile.write(b"Missing text parameter")
            return

        async def generate():
            communicate = edge_tts.Communicate(text, voice)
            audio_data = b""
            async for chunk in communicate.stream():
                if chunk["type"] == "audio":
                    audio_data += chunk["data"]
            return audio_data

        audio = asyncio.run(generate())

        self.send_response(200)
        self.send_header("Content-Type", "audio/mpeg")
        self.send_header("Content-Length", str(len(audio)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(audio)

    def log_message(self, fmt, *args):
        pass


if __name__ == "__main__":
    port = 5050
    server = HTTPServer(("127.0.0.1", port), TTSHandler)
    print("Edge TTS server running on http://127.0.0.1:%d" % port)
    server.serve_forever()

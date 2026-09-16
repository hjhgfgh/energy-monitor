using System;
using System.Collections.Generic;
using System.Net;
using System.Text;
using System.Threading;

namespace EmBench
{
    public static class Bench
    {
        // 结果容器（静态字段，便于跨线程聚合）
        public static long Ok, Blocked, Errors;
        public static readonly List<double> Latencies = new List<double>();
        static readonly object Lock = new object();

        public static void Run(string url, string token, int threads, int seconds)
        {
            ServicePointManager.DefaultConnectionLimit = threads * 2;
            var stopAt = DateTime.UtcNow.AddSeconds(seconds);
            var threadsList = new List<Thread>();
            for (int i = 0; i < threads; i++)
            {
                var t = new Thread(() => Worker(url, token, stopAt));
                t.IsBackground = true;
                t.Start();
                threadsList.Add(t);
            }
            foreach (var t in threadsList) t.Join();
        }

        static void Worker(string url, string token, DateTime stopAt)
        {
            while (DateTime.UtcNow < stopAt)
            {
                var sw = System.Diagnostics.Stopwatch.StartNew();
                int code = 0;
                try
                {
                    var req = (HttpWebRequest)WebRequest.Create(url);
                    req.Headers.Add("Authorization", "Bearer " + token);
                    req.Timeout = 5000;
                    req.Proxy = null;
                    using (var resp = (HttpWebResponse)req.GetResponse())
                    {
                        code = (int)resp.StatusCode;
                    }
                }
                catch (WebException ex)
                {
                    var resp = ex.Response as HttpWebResponse;
                    code = resp != null ? (int)resp.StatusCode : 0;
                }
                catch { code = 0; }
                sw.Stop();

                lock (Lock)
                {
                    if (code == 200) Ok++;
                    else if (code == 429) Blocked++;
                    else Errors++;
                    Latencies.Add(sw.Elapsed.TotalMilliseconds);
                }
            }
        }
    }
}

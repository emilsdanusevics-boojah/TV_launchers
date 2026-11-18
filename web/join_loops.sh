#!/usr/bin/env bash
# Joins the Apartment launcher's two camera loops into one seamless video that alternates
# between them, dissolving from one to the other over two seconds each way:
#
#   loop 1 (from 2 s in) → dissolve into loop 2 → loop 2 (from 2 s in) → dissolve into loop 1
#
# and round again. Each loop carries on through its dissolve (it's a loop, so its first
# seconds follow its last), so nothing jumps. The packed transparency mask dissolves along
# with the picture.
#
#   web/join_loops.sh <loop1.mp4> <loop2.mp4> <out.mp4>
set -euo pipefail
a=$1 b=$2 out=$3
fps=30 fade=2
f=$((fps * fade))
ffmpeg -loglevel error -y -i "$a" -i "$b" -filter_complex "
  [0:v]split=3[a1][a2][a3];
  [1:v]split=3[b1][b2][b3];
  [a1]trim=start_frame=$f,setpts=PTS-STARTPTS[A];
  [a2]trim=end_frame=$f,setpts=PTS-STARTPTS[ahead1];
  [a3]trim=end_frame=$f,setpts=PTS-STARTPTS[ahead2];
  [b1]trim=start_frame=$f,setpts=PTS-STARTPTS[B];
  [b2]trim=end_frame=$f,setpts=PTS-STARTPTS[bhead1];
  [b3]trim=end_frame=$f,setpts=PTS-STARTPTS[bhead2];
  [ahead1][bhead1]xfade=transition=fade:duration=$fade:offset=0[toB];
  [bhead2][ahead2]xfade=transition=fade:duration=$fade:offset=0[toA];
  [A][toB][B][toA]concat=n=4:v=1:a=0,format=yuv420p" \
  -r $fps -c:v libx264 -preset slow -crf 20 -profile:v high -level 4.1 \
  -bf 0 -g $((fps * 5)) -keyint_min $((fps * 5)) -sc_threshold 0 \
  -colorspace bt709 -color_primaries bt709 -color_trc bt709 -color_range tv \
  -movflags +faststart "$out"

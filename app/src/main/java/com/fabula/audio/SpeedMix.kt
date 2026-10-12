package com.fabula.audio

/** User-confirmed Audacity settings: x6 at unity, accelerated tracks at -36 dB.
 * Never normalize the mix: that would attenuate the main voice along with the layers.
 * The high-speed raw tracks remain independent of this output mix.
 */
object SpeedMix {
    const val FILTER144 = "[0:a]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=stereo[base];[1:a]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=stereo,volume=-36dB[fast144];[base][fast144]amix=inputs=2:duration=first:dropout_transition=0:normalize=0[out]"
    const val FILTER432 = "[0:a]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=stereo[base];[1:a]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=stereo,volume=-36dB[fast144];[2:a]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=stereo,volume=-36dB[fast432];[base][fast144][fast432]amix=inputs=3:duration=first:dropout_transition=0:normalize=0[out]"
}

**Suggested title**  
LTPO 1Hz to 120Hz jumps were a hidden trigger for my eyes (Vivo X300, PWM-sensitive)

**Post body**  
I want to share this in case it helps someone with sensitive eyes.

I bought the Chinese version of **Vivo X300**. At first I had noticeable eye strain, especially during longer sessions.  
At first I blamed only PWM, but after testing I realized that for me the **LTPO refresh jumps (1Hz -> 120Hz)** were also a big trigger.

What helped me:

* **Standard PWM mode:** ON  
* **Full high-frequency PWM (2100Hz):** OFF  
* **Color mode:** Professional  
* **Brightness:** around 90%  
* **Theme:** Light theme (dark mode is worse for my astigmatism)

I measured modulation depth with **OPPO Lightmaster**:

* Vivo X300 (my current setup): around **28%** (acceptable for me)  
* My previous phone (**OnePlus 13**): **60%+** (much worse for my eyes)

Also, because this is the Chinese version, I cannot properly change font style/weight. The default text looks too thin for me, and light theme helps reduce extra strain from that.

One more thing I noticed on my Vivo: in **light theme** the minimum refresh rate seems to stay around **60Hz**, while in **dark theme** it tends to sit at **120Hz**.  
For my eyes, the jump from **60Hz to 120Hz** feels less aggressive than the old **1Hz to 120Hz** behavior.

Another important point: LTPO is great for battery on paper, but for sensitive eyes constant jumps between very low and high refresh can feel rough.  
In my case, a simpler non-LTPO panel with stable refresh behavior might actually be easier.

So I made a small open-source Kotlin helper app that stabilizes refresh behavior:

* simple toggle UI  
* background service  
* auto-start after reboot

Project link: [android-force-120hz v1.1.0 release](https://github.com/svtxvt/android-force-120hz/releases/tag/v1.1.0)

Since this setup, the screen feels much calmer, and now I can use my Vivo X300 comfortably.

If you know Kotlin well, PRs are welcome.

> Not medical advice, just my personal experience.

Everyone is individual: this may help some people, and may not help others.
This specific behavior (theme-dependent minimum Hz) is what I observed on Vivo; I am not sure how it behaves on other brands.

# runehunter.gg — what to do with it

*You bought the domain at GoDaddy and have their free website plan. Short version: use the domain, skip the free site.*

---

## Why the GoDaddy free site can't do this job

GoDaddy's free Website Builder tier **does not support connecting your own domain.** Free sites live on a `something.godaddysites.com` subdomain; attaching `runehunter.gg` requires a paid plan. So the free builder can't actually serve the domain you just bought.

Even if it could, it's the wrong tool. What RuneHunter needs is one fast static page and a docs folder that lives next to the source. A drag-and-drop marketing builder gives you neither, and it puts your project's front door in a place you can't version-control.

**Use GitHub Pages instead.** It's free with no tier limits, supports custom domains with automatic HTTPS, and — the part that matters — serves straight from the repo, so the FAQ and API docs are versioned alongside the code that they document. Update the page by pushing a commit.

GoDaddy still earns its keep: it's your **registrar and DNS host**, which is all you actually need it for, and it's already paid for.

---

## Setup, about 20 minutes

### 1. Put the site in the repo

```
runehunter/
  docs/            ← GitHub Pages serves from here
    index.html     ← the landing page (delivered)
    CNAME          ← one line: runehunter.gg
    integration-api.md
    FAQ.md
```

The `CNAME` file is literally one line with no protocol and no trailing slash:

```
runehunter.gg
```

### 2. Turn Pages on

In the GitHub repo → **Settings → Pages** → Source: **Deploy from a branch** → Branch `main`, folder `/docs` → Save.

### 3. Point DNS at GitHub (in GoDaddy)

GoDaddy → **My Products → runehunter.gg → DNS → Manage Zones**.

Delete GoDaddy's default parking records first — there'll be an `A` record on `@` pointing at their parking IP and usually a `CNAME` on `www`. Then add:

| Type | Name | Value | TTL |
|---|---|---|---|
| A | @ | `185.199.108.153` | 1 hour |
| A | @ | `185.199.109.153` | 1 hour |
| A | @ | `185.199.110.153` | 1 hour |
| A | @ | `185.199.111.153` | 1 hour |
| CNAME | www | `<your-github-username>.github.io` | 1 hour |

All four A records — they're GitHub's load-balanced set, not alternatives. Confirm the current IPs against GitHub's Pages docs when you do this; they change rarely but they do change.

### 4. Enable HTTPS

Back in **Settings → Pages**, wait for the custom domain check to go green (usually 10–60 minutes, occasionally a few hours), then tick **Enforce HTTPS**. GitHub provisions the certificate for you.

### 5. Free email forwarding

GoDaddy includes basic email forwarding with a domain. Set up `hello@runehunter.gg` → your personal inbox. Put that address on the GitHub repo, in the Plugin Hub PR, and in the plugin's config panel. It costs nothing and it's the difference between a project that looks maintained and one that looks like a weekend build.

---

## The one rule

**The plugin must never make an HTTP request to runehunter.gg.** Not for version checks, not for news banners, not for analytics.

The moment it does, your Plugin Hub review stops being "client-side collection plugin" and becomes a privacy-disclosure conversation about what data goes to a third-party server — which is precisely the conversation that dominated the JebScape submission thread. Right now the only networking in RuneHunter is RuneLite's own Party service, which is a well-precedented, easily-explained position. Keep it that way.

A website is fine. A website the plugin talks to is a different review.

---

## What to put on it, in order

1. **The landing page** — delivered as `index.html`. Self-contained, no build step, no dependencies, no tracking. Drop it in `docs/` and it works.
2. **The FAQ** — already on the landing page in short form; link the full `FAQ.md` for the long answers.
3. **Install instructions** — on the page. Update the Plugin Hub link once you're merged.
4. **The API docs** — `integration-api.md`. Being able to say "runehunter.gg has the API docs" is what makes a third-party developer take the API seriously.
5. **Screenshots and GIFs** — the single biggest upgrade available. A shiny spawning behind a wall, a mini-Jad companion, a prayer-flick deflect. Add them to both the page and the repo README when the art lands.

---

## Also worth doing today

**Turn on WHOIS privacy** in GoDaddy if it isn't already. It's free or near-free, and it keeps your home address off a public record attached to a plugin that touches people's game accounts.

**Check auto-renew is on.** A `.gg` that lapses gets grabbed within hours, and by then it'll be in video descriptions.

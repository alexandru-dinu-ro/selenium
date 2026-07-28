Follow-up: Symantec extension force-install blocking Edge automation (InPrivate)

Hi team,

Follow-up to my earlier request about BrowserSignin. We've isolated a second, related issue on the same automation machine.

Our Selenium/Edge automation runs in InPrivate mode. Edge is now failing to launch cleanly with the error:

"Can't find extensions. Extensions with the IDs [lgliocaeggimgcpgbbejhdnbmajgaii, pldkfpaadpkjhggaejnlfmeneclddkhj] can't be located. Contact your administrator."

We've identified these as the two Symantec-managed extensions force-installed on this machine. The dialog is non-dismissable and appears to prevent Edge from completing startup in InPrivate mode, which breaks our automated test sessions entirely (Selenium times out with a DevToolsActivePort error since Edge never finishes initializing).

Could you help with one of the following, whichever is compliant on your end?

1. Exclude this automation machine/service account from the Symantec extension force-install policy, if endpoint monitoring isn't required for automated test traffic, or
2. Explicitly allow these two extension IDs to run in InPrivate windows (via Symantec's admin console or Edge's ExtensionSettings incognito allow-list), if exclusion isn't an option.

Machine name: [FILL IN]
Extension IDs: lgliocaeggimgcpgbbejhdnbmajgaii, pldkfpaadpkjhggaejnlfmeneclddkhj

Happy to provide more logs or hop on a call. Thanks for looking into this.

[Your name]
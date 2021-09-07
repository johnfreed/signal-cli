A2X = a2x
GZIP = gzip
INSTALL = install

MANPAGESRC = signal-cli-dbus.5 signal-cli.1
PREFIX = /usr/local
MANDIR = $(PREFIX)/share/man

.PHONY: all
all: $(MANPAGESRC)

%: %.adoc
	@echo "Generating manpage for $@"
	$(A2X) --no-xmllint --doctype manpage --format manpage "$^"
	$(GZIP) -f "$@"

clean:
	rm -f *.gz

install:
	$(INSTALL) -m 0644 -t $(MANDIR)/man1 signal-cli.1.gz
	$(INSTALL) -m 0644 -t $(MANDIR)/man5 signal-cli-dbus.5.gz

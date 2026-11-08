import {themes as prismThemes} from 'prism-react-renderer';

// This runs in Node.js - Don't use client-side code here (browser APIs, JSX...)

/** @type {import('@docusaurus/types').Config} */
const config = {
    title: 'Cloudhopper MC',
    tagline: 'Write once, run anywhere. Revisited for serverless Java.',
    favicon: 'img/favicon.ico',

    customFields: {
        description:
            'Write once, run anywhere, revisited: Cloudhopper MC deploys the same annotated Java functions ' +
            'to AWS Lambda, Azure Functions, Google Cloud Functions or a Spring Boot service on your own ' +
            'servers. Switching providers is a build setting. Open source by Dukehoff GmbH.',
    },

    // Set the production url of your site here
    url: 'https://cloudhopper.eu',
    // Set the /<baseUrl>/ pathname under which your site is served
    // For GitHub pages deployment, it is often '/<projectName>/'
    baseUrl: '/',

    // GitHub pages deployment config.
    // If you aren't using GitHub pages, you don't need these.
    organizationName: 'dukehoff-gmbh', // Usually your GitHub org/user name.
    projectName: 'dukehoff-cloudhopper', // Usually your repo name.

    onBrokenLinks: 'throw',
    onBrokenMarkdownLinks: 'warn',

    // Even if you don't use internationalization, you can use this field to set
    // useful metadata like html lang. For example, if your site is Chinese, you
    // may want to replace "en" with "zh-Hans".
    i18n: {
        defaultLocale: 'en',
        locales: ['en'],
    },

    // Enable Mermaid support
    markdown: {
        mermaid: true,
    },

    // Pull in the Mermaid theme
    themes: [
        '@docusaurus/theme-mermaid',
    ],

    presets: [
        [
            'classic',
            /** @type {import('@docusaurus/preset-classic').Options} */
            ({
                docs: {
                    sidebarPath: './sidebars.js',
                },
//        blog: {
//          showReadingTime: true,
//          feedOptions: {
//            type: ['rss', 'atom'],
//            xslt: true,
//          },
//          // Useful options to enforce blogging best practices
//          onInlineTags: 'warn',
//          onInlineAuthors: 'warn',
//          onUntruncatedBlogPosts: 'warn',
//        },
                theme: {
                    customCss: './src/css/custom.css',
                },
            }),
        ],
    ],

    themeConfig:
            /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
                    ({
                        // Replace with your project's social card
                        image: 'img/social-card.png',
                        metadata: [
                            {name: 'keywords', content: 'Java, serverless, multi-cloud, AWS Lambda, Azure Functions, Google Cloud Functions, Terraform, OpenAPI, annotation processor'},
                        ],
                        navbar: {
                            title: 'Cloudhopper MC',
                            logo: {
                                alt: 'Cloudhopper MC logo',
                                src: 'img/logo.svg',
                            },
                            items: [
                                {
                                    type: 'docSidebar',
                                    sidebarId: 'documentationSidebar',
                                    position: 'left',
                                    label: 'Documentation',
                                },
                                {
                                    href: 'https://github.com/dukehoff-gmbh/dukehoff-cloudhopper',
                                    label: 'GitHub',
                                    position: 'right',
                                },
                            ],
                        },
                        footer: {
                            style: 'dark',
                            links: [
                                {
                                    title: 'Docs',
                                    items: [
                                        {
                                            label: 'Overview',
                                            to: '/docs/intro',
                                        },
                                        {
                                            label: 'Quickstart',
                                            to: '/docs/guides/quickstart',
                                        },
                                        {
                                            label: 'Writing functions',
                                            to: '/docs/guides/writing-functions',
                                        },
                                    ],
                                },
                                {
                                    title: 'Project',
                                    items: [
                                        {
                                            label: 'GitHub',
                                            href: 'https://github.com/dukehoff-gmbh/dukehoff-cloudhopper',
                                        },
                                        {
                                            label: 'Issues',
                                            href: 'https://github.com/dukehoff-gmbh/dukehoff-cloudhopper/issues',
                                        },
                                        {
                                            label: 'Roadmap',
                                            to: '/docs/roadmap',
                                        },
                                    ],
                                },
                                {
                                    title: 'Dukehoff GmbH',
                                    items: [
                                        {
                                            label: 'dukehoff.com',
                                            href: 'https://dukehoff.com/en/',
                                        },
                                        {
                                            label: 'Imprint (Impressum)',
                                            to: '/impressum',
                                        },
                                        {
                                            label: 'Privacy (Datenschutz)',
                                            to: '/datenschutz',
                                        },
                                    ],
                                },
                            ],
                            copyright: `Copyright © ${new Date().getFullYear()} Dukehoff GmbH. Built with Docusaurus.`,
                        },
                        prism: {
                            theme: prismThemes.github,
                            darkTheme: prismThemes.dracula,
                            additionalLanguages: ['java', 'bash', 'hcl'],
                        },
                        mermaid: {
                            theme: {light: 'neutral', dark: 'forest'},
                        },
                    }),
        };

        export default config;

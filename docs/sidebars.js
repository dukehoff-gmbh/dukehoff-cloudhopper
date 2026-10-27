/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
    documentationSidebar: [
        {
            type: 'doc',
            id: 'intro',
            label: 'Overview',
        },
        {
            type: 'category',
            label: 'Getting Started',
            collapsed: false,
            items: [
                'guides/quickstart',
                'guides/writing-functions',
                'modules/maven-archetype',
                {
                    type: 'category',
                    label: 'Provider Setup',
                    collapsed: true,
                    items: [
                        'setup/aws-prerequisites',
//                        'setup/gcp-prerequisites',
//                        'setup/azure-prerequisites',
                    ],
                }
            ],
        },
        {
            type: 'category',
            label: 'Core Concepts',
            collapsed: true,
            items: [
                'modules/deployment-config-api',
                'modules/deployment-config-generator-api',
                'modules/deployment-config-generator-generic',
                        // planned: 'concepts/how-annotations-work',
                        // planned: 'concepts/templating-engine',
            ],
        },
        {
            type: 'category',
            label: 'Cloud Providers',
            collapsed: true,
            items: [
                'platforms/generator-overview',
                {
                    type: 'category',
                    label: 'AWS',
                    items: [
                        'modules/generator-aws-terraform',
                        'modules/provider-aws',
                    ],
                },
                {
                    type: 'category',
                    label: 'Azure',
                    items: [
                        'modules/generator-azure-terraform',
                        'modules/provider-azure',
                    ],
                },
                {
                    type: 'category',
                    label: 'GCP',
                    items: [
                        'modules/generator-gcp-terraform',
                        'modules/provider-gcp',
                    ],
                },
            ],
        },
        {
            type: 'doc',
            id: 'roadmap',
            label: 'Roadmap',
        },
    ],
};

export default sidebars;
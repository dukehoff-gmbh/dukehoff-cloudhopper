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
                'guides/customizing-targets',
                'modules/maven-archetype',
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
                'modules/overview',
                        // planned: 'concepts/how-annotations-work',
                        // planned: 'concepts/templating-engine',
            ],
        },
        {
            type: 'category',
            label: 'Cloud Providers',
            collapsed: true,
            items: [
                {type: 'doc', id: 'platforms/generator-overview', label: 'Overview'},
                {
                    type: 'category',
                    label: 'AWS',
                    items: [
                        {type: 'doc', id: 'setup/aws-prerequisites', label: 'Setup'},
                        'platforms/aws',
                    ],
                },
                {
                    type: 'category',
                    label: 'Azure',
                    items: [
                        {type: 'doc', id: 'setup/azure-prerequisites', label: 'Setup'},
                        'platforms/azure',
                    ],
                },
                {
                    type: 'category',
                    label: 'GCP',
                    items: [
                        {type: 'doc', id: 'setup/gcp-prerequisites', label: 'Setup'},
                        'platforms/gcp',
                    ],
                },
                'platforms/springboot',
                {
                    type: 'category',
                    label: 'Knative',
                    items: [
                        {type: 'doc', id: 'setup/knative-prerequisites', label: 'Setup'},
                        'platforms/knative',
                    ],
                },
                {
                    type: 'category',
                    label: 'Scaleway',
                    items: [
                        {type: 'doc', id: 'setup/scaleway-prerequisites', label: 'Setup'},
                        'platforms/scaleway',
                    ],
                },
            ],
        },
        {
            type: 'doc',
            id: 'release-notes',
            label: 'Release notes',
        },
    ],
};

export default sidebars;